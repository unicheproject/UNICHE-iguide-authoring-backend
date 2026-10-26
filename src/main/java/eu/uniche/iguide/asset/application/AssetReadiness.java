package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetTranslation;
import eu.uniche.iguide.asset.domain.AssetType;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.asset.infrastructure.AssetTranslationRepository;
import eu.uniche.iguide.asset.infrastructure.ResourceAssetRepository;
import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Readiness of Assets ({@code 03} section 1, {@code 14} section 2; DECISIONS.md D-033): an ACTIVE image that is not
 * decorative needs alternative text in the default language; an audio or video Asset bound to ACTIVE content warns
 * without a transcript in the default language.
 */
@Component
public class AssetReadiness implements ReadinessCheck {

    private final AssetRepository assets;
    private final AssetTranslationRepository translations;
    private final ResourceAssetRepository bindings;

    public AssetReadiness(AssetRepository assets, AssetTranslationRepository translations,
                          ResourceAssetRepository bindings) {
        this.assets = assets;
        this.translations = translations;
        this.bindings = bindings;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        String language = context.defaultLanguage();
        if (language == null) {
            return;
        }
        Map<UUID, Asset> evaluated = new LinkedHashMap<>();
        assets.findByProjectIdAndStatus(context.projectId(), ResourceStatus.ACTIVE).stream()
                .filter(a -> a.getType() == AssetType.IMAGE && !a.isDecorative())
                .forEach(a -> evaluated.put(a.getId(), a));
        Set<UUID> heard = bindings.findByProjectId(context.projectId()).stream()
                .filter(b -> context.index().status(b.getResourceType().name(), b.getResourceId())
                        == ResourceStatus.ACTIVE)
                .map(ResourceAsset::getAssetId)
                .collect(Collectors.toSet());
        List<Asset> media = heard.isEmpty() ? List.of() : assets.findByProjectIdAndIdIn(context.projectId(), heard);
        media.stream()
                .filter(a -> (a.getType() == AssetType.AUDIO || a.getType() == AssetType.VIDEO)
                        && a.getStatus() != ResourceStatus.ARCHIVED)
                .forEach(a -> evaluated.put(a.getId(), a));
        if (evaluated.isEmpty()) {
            return;
        }
        Map<UUID, AssetTranslation> defaults = translations
                .findByProjectIdAndAssetIdIn(context.projectId(), new ArrayList<>(evaluated.keySet())).stream()
                .filter(t -> Objects.equals(t.getLanguageCode(), language))
                .collect(Collectors.toMap(AssetTranslation::getAssetId, t -> t, (a, b) -> a));
        for (Asset asset : evaluated.values()) {
            AssetTranslation texts = defaults.get(asset.getId());
            String path = "translations." + language + ".";
            if (asset.getType() == AssetType.IMAGE) {
                if (texts == null || ReadinessReport.blank(texts.getAlternativeText())) {
                    report.block("ASSET", asset.getId(), asset.getInternalName(), language, path + "alternativeText",
                            "MISSING_ALTERNATIVE_TEXT");
                }
            } else if (texts == null || ReadinessReport.blank(texts.getTranscript())) {
                report.warn("ASSET", asset.getId(), asset.getInternalName(), language, path + "transcript",
                        "MISSING_TRANSCRIPT");
            }
        }
    }
}
