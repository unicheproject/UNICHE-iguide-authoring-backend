package eu.uniche.iguide.readiness.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.readiness.application.GetReadinessQuery;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/projects/{projectId}/readiness} ({@code 07} section 14, DECISIONS.md D-033): what blocks the
 * Application from going live and what deserves attention, computed on request.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/readiness")
public class ReadinessController {

    private final GetReadinessQuery getReadiness;

    public ReadinessController(GetReadinessQuery getReadiness) {
        this.getReadiness = getReadiness;
    }

    @GetMapping
    public GetReadinessQuery.Readiness get(@PathVariable UUID projectId) {
        return getReadiness.execute(projectId, CurrentPrincipal.require());
    }
}
