package eu.uniche.iguide.translation.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Readiness warning {@code AI_OUTPUT_UNREVIEWED} ({@code 12} section 3, {@code 03} section 1; DECISIONS.md D-035,
 * D-036): live text in an enabled language that is an unreviewed machine translation. On an ACTIVE Resource at path
 * {@code translations.<lang>}; for a stop or block, on its ACTIVE Route or Guide at
 * {@code stops[i].translations.<lang>} or {@code blocks[i].translations.<lang>}; on an ACTIVE Asset at
 * {@code translations.<lang>}. What a working copy carries is not live yet and counts on apply.
 */
@Component
public class MachineTranslationReadiness implements ReadinessCheck {

    private static final Map<String, String> CHILDREN = Map.of(ProvenanceStore.ROUTE_STOP, "stops",
            ProvenanceStore.GUIDE_BLOCK, "blocks");

    private final ProvenanceStore store;
    private final AssetRepository assets;

    public MachineTranslationReadiness(ProvenanceStore store, AssetRepository assets) {
        this.store = store;
        this.assets = assets;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<ProvenanceStore.Entry> unreviewed = store.machineGenerated(context.projectId()).stream()
                .filter(entry -> !entry.pending() && ProvenanceStore.unreviewed(entry.provenance())
                        && context.languages().contains(entry.languageCode()))
                .toList();
        if (unreviewed.isEmpty()) {
            return;
        }
        Map<UUID, Integer> positions = unreviewed.stream().anyMatch(entry -> entry.parentType() != null)
                ? store.childPositions(context.projectId()) : Map.of();
        Map<UUID, String> activeAssets = unreviewed.stream()
                .anyMatch(entry -> ProvenanceStore.ASSET.equals(entry.resourceType()))
                ? assets.findByProjectIdAndStatus(context.projectId(), ResourceStatus.ACTIVE).stream()
                        .collect(Collectors.toMap(Asset::getId, Asset::getInternalName))
                : Map.of();
        for (ProvenanceStore.Entry entry : unreviewed) {
            String language = entry.languageCode();
            if (ProvenanceStore.ASSET.equals(entry.resourceType())) {
                if (activeAssets.containsKey(entry.resourceId())) {
                    report.warn(ProvenanceStore.ASSET, entry.resourceId(), activeAssets.get(entry.resourceId()),
                            language, "translations." + language, "AI_OUTPUT_UNREVIEWED");
                }
            } else if (entry.parentType() != null) {
                Integer position = positions.get(entry.resourceId());
                if (position != null && entry.parentId() != null
                        && context.index().status(entry.parentType(), entry.parentId()) == ResourceStatus.ACTIVE) {
                    report.warn(entry.parentType(), entry.parentId(),
                            context.index().title(entry.parentType(), entry.parentId()), language,
                            CHILDREN.get(entry.resourceType()) + "[" + position + "].translations." + language,
                            "AI_OUTPUT_UNREVIEWED");
                }
            } else if (context.index().status(entry.resourceType(), entry.resourceId()) == ResourceStatus.ACTIVE) {
                report.warn(entry.resourceType(), entry.resourceId(),
                        context.index().title(entry.resourceType(), entry.resourceId()), language,
                        "translations." + language, "AI_OUTPUT_UNREVIEWED");
            }
        }
    }
}
