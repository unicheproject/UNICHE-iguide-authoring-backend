package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueBadResponseException;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.project.domain.Project;
import eu.uniche.iguide.project.infrastructure.ProjectRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place the companion row is written.
 *
 * <p>Neither operation here decides when it is called. The ordering rule belongs to the callers: write
 * to the Catalogue first, and synchronize the companion row only after the Catalogue confirms.
 *
 * <p>Those two writes are not one transaction and cannot be, because they are different systems. No
 * compensating action or retry is attempted. If the local write fails after a successful Catalogue
 * write the row is briefly wrong, and the next read of that Project repairs it — that is what lazy
 * reconciliation is for, and it is why the periodic reconciliation sweep is unnecessary.
 */
@Service
public class ProjectCompanionSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProjectCompanionSyncService.class);

    private final ProjectRepository repository;

    public ProjectCompanionSyncService(ProjectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Project upsert(CatalogueProjectDto dto) {
        UUID projectId = requiredUuid(dto.id(), "project id");
        UUID organizationId = requiredUuid(dto.orgId(), "organization id");
        Instant now = Instant.now();

        return repository.findById(projectId)
                .map(existing -> {
                    existing.applyCatalogueState(
                            organizationId, dto.name(), dto.slug(), dto.status(), now);
                    return existing;
                })
                .orElseGet(() -> repository.save(Project.companionOf(
                        projectId, organizationId, dto.name(), dto.slug(), dto.status(), now)));
    }

    /**
     * Reads the companion row of a Project whose access has already been checked. Absence here means the
     * Catalogue confirmed the Project but the local write did not land, which the next read repairs.
     */
    @Transactional(readOnly = true)
    public Project get(UUID projectId) {
        return repository.findById(projectId).orElseThrow(() -> new ResourceNotFoundException(
                "No local companion row for project " + projectId));
    }

    /** Writes the one field of this row the Catalogue neither stores nor cares about. */
    @Transactional
    public Project setLocalDescription(UUID projectId, String localDescription) {
        Project companion = repository.findById(projectId).orElseThrow(
                () -> new ResourceNotFoundException("No local companion row for project " + projectId));
        companion.setLocalDescription(localDescription);
        return companion;
    }

    /**
     * Records that the Catalogue no longer recognizes this Project. Called only on a definite
     * {@code 404}; a Catalogue outage must never reach here, or a single outage would soft-delete every
     * companion row.
     */
    @Transactional
    public void softDelete(UUID projectId) {
        Optional<Project> project = repository.findById(projectId);
        if (project.isEmpty()) {
            // Nothing to reconcile: the Catalogue does not recognize a Project this backend never
            // mirrored.
            return;
        }
        Project companion = project.get();
        if (companion.isLocallyDeleted()) {
            return;
        }
        companion.markLocallyDeleted(Instant.now());
        log.info("Companion row for project {} soft-deleted after a Catalogue 404", projectId);
    }

    private static UUID requiredUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new CatalogueBadResponseException(
                    "The Catalogue response carries no " + field);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new CatalogueBadResponseException(
                    "The Catalogue " + field + " is not a UUID", notAUuid);
        }
    }
}
