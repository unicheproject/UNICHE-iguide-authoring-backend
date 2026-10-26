package eu.uniche.iguide.translation.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/v1/projects/{projectId}/ai-provenance} ({@code 12} section 3; DECISIONS.md D-035): every machine
 * translation of the Project's Resources, their stops and blocks, and its Assets, for the Translation workspace's AI markers and its "unreviewed AI output"
 * filter. Live rows and what working copies carry ({@code pending}); a language can have one of each.
 */
@Service
public class AiProvenanceQuery {

    private final ProjectAccessGateway accessGateway;
    private final ProvenanceStore store;
    private final TransactionTemplate transactionTemplate;

    public AiProvenanceQuery(ProjectAccessGateway accessGateway, ProvenanceStore store,
                             TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.store = store;
        this.transactionTemplate = transactionTemplate;
    }

    public List<ProvenanceStore.Entry> execute(UUID projectId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return store.machineGenerated(projectId);
        });
    }
}
