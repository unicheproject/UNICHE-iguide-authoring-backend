package eu.uniche.iguide.platform.projectaccess;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueNotFoundException;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.project.application.ProjectCompanionSyncService;
import eu.uniche.iguide.project.domain.Project;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Performs the per-request Project access check and, as a side effect, reconciles the companion row.
 *
 * <p>The read path <em>is</em> the reconciliation mechanism:
 *
 * <ul>
 *   <li>{@code 200} — the companion row is upserted from the response;
 *   <li>{@code 404} — the companion row is soft-deleted, and only then does the {@code 404} propagate;
 *   <li>any {@code 5xx} or timeout — nothing local changes.
 * </ul>
 *
 * <p>That last case is the one that matters. An unavailable Catalogue is not evidence that a Project is
 * gone, and treating it as such would soft-delete companion rows across an entire outage.
 *
 * <p>A short-TTL cache keyed by issuer, subject, and Project id is permitted by the design and is
 * deliberately not added yet: correctness first, and one Catalogue call per request is not currently a
 * measured problem.
 */
@Service
public class ProjectAccessService implements ProjectAccessGateway {

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public ProjectAccessService(CatalogueClient catalogueClient,
                                ProjectCompanionSyncService companionSync) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    @Override
    public ProjectContext requireAccess(UUID projectId, UnichePrincipal principal) {
        AccessibleProject project = requireAccessibleProject(projectId, principal);
        return new ProjectContext(project.id(), project.organizationId(), principal);
    }

    @Override
    public AccessibleProject requireAccessibleProject(UUID projectId, UnichePrincipal principal) {
        CatalogueProjectDto dto;
        try {
            dto = catalogueClient.getProject(projectId);
        } catch (CatalogueNotFoundException gone) {
            companionSync.softDelete(projectId);
            throw gone;
        }
        Project companion = synchronize(dto, projectId);
        return new AccessibleProject(
                companion.getId(),
                companion.getOrganizationId(),
                companion.getCatalogueName(),
                companion.getCatalogueSlug(),
                companion.getCatalogueStatus(),
                // The Catalogue does not currently return its own creation and update timestamps for a
                // Project. The fields stay in the contract for when it does.
                companion.getCatalogueCreatedAt(),
                companion.getCatalogueUpdatedAt());
    }

    /**
     * A page fires several requests at once, and each one synchronizes the same row. The loser of that
     * race, an optimistic-lock failure on update or a duplicate key on the first insert, lost nothing:
     * the winner wrote the same Catalogue answer moments earlier. So the row is re-read rather than the
     * request failed with a {@code 409} the caller never caused.
     */
    private Project synchronize(CatalogueProjectDto dto, UUID projectId) {
        try {
            return companionSync.upsert(dto);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException concurrentSync) {
            return companionSync.get(projectId);
        }
    }
}
