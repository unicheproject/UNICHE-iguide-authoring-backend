package eu.uniche.iguide.translation.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.translation.application.AiProvenanceQuery;
import eu.uniche.iguide.translation.application.ProvenanceStore;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The machine translations of a Project ({@code 12} section 3; DECISIONS.md D-035, D-036): {@code [{resourceType,
 * resourceId, parentType, parentId, languageCode, provenance, pending}]}, {@code provenance} in the {@code 03} section
 * 1 shape. {@code resourceType} is a Resource's working-copy type, {@code ROUTE_STOP} or {@code GUIDE_BLOCK} (with the
 * Route or Guide as parent), or {@code ASSET}.
 */
@RestController
public class AiProvenanceController {

    private final AiProvenanceQuery query;

    public AiProvenanceController(AiProvenanceQuery query) {
        this.query = query;
    }

    @GetMapping("/api/v1/projects/{projectId}/ai-provenance")
    public List<ProvenanceStore.Entry> list(@PathVariable UUID projectId) {
        return query.execute(projectId, CurrentPrincipal.require());
    }
}
