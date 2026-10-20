package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.AssetTranslation;
import eu.uniche.iguide.asset.domain.AssetUsage;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.asset.infrastructure.AssetTranslationRepository;
import eu.uniche.iguide.asset.infrastructure.ResourceAssetRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lookups and rules the Asset use cases share. Every method takes the Project explicitly; callers have
 * already passed the Project access check.
 */
@Component
public class AssetRules {

    /** Long enough to browse a library page, short enough that a leaked URL soon stops working. */
    public static final Duration URL_VALIDITY = Duration.ofMinutes(15);
    static final int MAX_TEXT = 2000;
    static final int MAX_TRANSCRIPT = 100_000;

    private final AssetRepository assets;
    private final AssetTranslationRepository translations;
    private final ResourceAssetRepository bindings;
    private final AssetStorage storage;
    private final ProjectLanguageLookup languages;
    private final Map<AssetResourceType, AssetBindingTarget> targets = new EnumMap<>(AssetResourceType.class);
    private final List<AssetReferenceSource> referenceSources;

    public AssetRules(AssetRepository assets, AssetTranslationRepository translations, ResourceAssetRepository bindings,
                      AssetStorage storage, ProjectLanguageLookup languages, List<AssetBindingTarget> targets,
                      List<AssetReferenceSource> referenceSources) {
        this.assets = assets;
        this.translations = translations;
        this.bindings = bindings;
        this.storage = storage;
        this.languages = languages;
        targets.forEach(target -> this.targets.put(target.kind(), target));
        this.referenceSources = referenceSources;
    }

    public Asset requireAsset(UUID projectId, UUID assetId) {
        return assets.findByProjectIdAndId(projectId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("No such asset in this project."));
    }

    public AssetView view(Asset asset) {
        return views(asset.getProjectId(), List.of(asset)).get(0);
    }

    /**
     * Views of several Assets of one Project, loaded with one query per table. Usage counts Resources, not
     * bindings: an image that is a Point of Interest's hero image and in its gallery is used once.
     */
    public List<AssetView> views(UUID projectId, List<Asset> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Asset::getId).toList();
        Map<UUID, List<AssetTranslation>> texts = translations.findByProjectIdAndAssetIdIn(projectId, ids).stream()
                .collect(Collectors.groupingBy(AssetTranslation::getAssetId));
        Map<UUID, Long> usage = new HashMap<>(bindings.findByProjectIdAndAssetIdIn(projectId, ids).stream()
                .collect(Collectors.groupingBy(ResourceAsset::getAssetId,
                        Collectors.mapping(b -> b.getResourceType() + "/" + b.getResourceId(),
                                Collectors.collectingAndThen(Collectors.toSet(), set -> (long) set.size())))));
        referenceSources.forEach(source -> source.countsByAsset(projectId, ids).forEach((id, n) ->
                usage.merge(id, n, Long::sum)));
        Instant expires = Instant.now().plus(URL_VALIDITY);
        return list.stream().map(a -> new AssetView(a, texts.getOrDefault(a.getId(), List.of()),
                storage.signedReadUrl(a.getStorageKey(), URL_VALIDITY), expires, usage.getOrDefault(a.getId(), 0L)))
                .toList();
    }

    public List<AssetTranslation> translationsOf(Asset asset) {
        return translations.findByProjectIdAndAssetId(asset.getProjectId(), asset.getId());
    }

    public void checkLanguages(UUID projectId, Set<String> codes, List<ErrorItem> errors) {
        Set<String> enabled = languages.enabledCodes(projectId);
        codes.stream().filter(code -> !enabled.contains(code)).sorted().forEach(code -> errors.add(
                new ErrorItem("translations." + code, "UNKNOWN_LANGUAGE", "Is not a language of this project.")));
    }

    public Set<String> enabledLanguages(UUID projectId) {
        return languages.enabledCodes(projectId);
    }

    public String defaultLanguage(UUID projectId) {
        return languages.requireDefaultLanguage(projectId);
    }

    public AssetBindingTarget target(AssetResourceType kind) {
        AssetBindingTarget target = targets.get(kind);
        if (target == null) {
            throw new IllegalStateException("No binding target for " + kind);
        }
        return target;
    }

    /** The status of a Resource that binds Assets, or {@code 404} when it is not in this Project. */
    public ResourceStatus requireResource(UUID projectId, AssetResourceType kind, UUID resourceId) {
        return target(kind).status(projectId, resourceId).orElseThrow(() ->
                new ResourceNotFoundException("No such " + kind.label() + " in this project."));
    }

    /**
     * An Asset the given usage may bind: in this Project, not archived, of an accepted type. Adds an error at
     * {@code path} otherwise.
     */
    public Optional<Asset> checkBindable(UUID projectId, UUID assetId, AssetUsage usage, String path,
                                         List<ErrorItem> errors) {
        if (assetId == null) {
            errors.add(new ErrorItem(path, "REQUIRED", "Choose an asset."));
            return Optional.empty();
        }
        Optional<Asset> asset = assets.findByProjectIdAndId(projectId, assetId);
        if (asset.isEmpty()) {
            errors.add(new ErrorItem(path, "RESOURCE_NOT_FOUND", "No such asset in this project."));
        } else if (asset.get().getStatus() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem(path, "ARCHIVED_REFERENCE", "The asset is archived."));
        } else if (usage != null && !usage.accepts(asset.get().getType())) {
            errors.add(new ErrorItem(path, "WRONG_ASSET_TYPE", "A " + usage + " must be "
                    + usage.accepted().stream().map(t -> t.label()).sorted().collect(Collectors.joining(" or "))
                    + ", not " + asset.get().getType().label() + "."));
        } else {
            return asset;
        }
        return Optional.empty();
    }

    /** One role of the Asset in a Resource; {@code languageCode} is null for every language. */
    public record Usage(String usage, String languageCode) {
    }

    /** One Resource or Floor Plan that uses the Asset, with its roles there. */
    public record ResourceUsage(ResourceReference resource, List<Usage> usages) {
    }

    /** Where the Asset is used, archived users included ({@code GET .../usages}). */
    public List<ResourceUsage> usagesOf(UUID projectId, UUID assetId) {
        Map<AssetResourceType, Map<UUID, List<Usage>>> byKind = new EnumMap<>(AssetResourceType.class);
        for (ResourceAsset b : bindings.findByProjectIdAndAssetId(projectId, assetId)) {
            List<Usage> roles = byKind.computeIfAbsent(b.getResourceType(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(b.getResourceId(), k -> new ArrayList<>());
            Usage role = new Usage(b.getUsage().name(), b.getLanguageCode());
            if (!roles.contains(role)) {
                roles.add(role);
            }
        }
        List<ResourceUsage> result = new ArrayList<>();
        byKind.forEach((kind, roles) -> target(kind).references(projectId, roles.keySet()).forEach(reference ->
                result.add(new ResourceUsage(reference, roles.getOrDefault(reference.resourceId(), List.of())))));
        referenceSources.forEach(source -> source.referencesTo(projectId, assetId).forEach(reference ->
                result.add(new ResourceUsage(reference, List.of(new Usage(source.usage(), null))))));
        return result;
    }

    /**
     * What keeps an Asset in use ({@code 03} section 1, {@code 07} section 14): non-archived Resources that
     * bind it, INLINE included, and non-archived Floor Plans that show it.
     */
    public List<ResourceReference> referencesTo(UUID projectId, UUID assetId) {
        return usagesOf(projectId, assetId).stream().map(ResourceUsage::resource)
                .filter(r -> r.status() != ResourceStatus.ARCHIVED).toList();
    }
}
