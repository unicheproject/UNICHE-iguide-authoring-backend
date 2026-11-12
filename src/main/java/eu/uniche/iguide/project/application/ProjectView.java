package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueBadResponseException;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import eu.uniche.iguide.platform.projectaccess.AccessibleProject;
import eu.uniche.iguide.project.application.ProjectInitializationReader.ProjectInitialization;
import eu.uniche.iguide.project.domain.Project;
import java.time.Instant;
import java.util.UUID;

/**
 * A Project as the application layer hands it upward, whether it came from a completed access check or
 * straight from a passthrough listing.
 *
 * <p>{@code createdAt} and {@code updatedAt} are the Catalogue's own times for the Project, not those of
 * the companion row; either is {@code null} when the Catalogue has not sent a readable value.
 *
 * <p>{@code initialization} is what initialization chose ({@code 12} section 3); it is
 * {@link ProjectInitialization#none()} where the reader was not asked: listings, creation, and restore.
 */
public record ProjectView(UUID id,
                          UUID organizationId,
                          String name,
                          String slug,
                          String status,
                          String localDescription,
                          boolean initialized,
                          Instant createdAt,
                          Instant updatedAt,
                          ProjectInitialization initialization) {

    public static ProjectView of(AccessibleProject project, String localDescription,
                                 boolean initialized) {
        return new ProjectView(project.id(), project.organizationId(), project.name(),
                project.slug(), project.status(), localDescription, initialized,
                project.createdAt(), project.updatedAt(), ProjectInitialization.none());
    }

    public static ProjectView ofCompanion(Project companion) {
        return new ProjectView(companion.getId(), companion.getOrganizationId(),
                companion.getCatalogueName(), companion.getCatalogueSlug(),
                companion.getCatalogueStatus(), companion.getLocalDescription(),
                companion.getInitializedAt() != null,
                companion.getCatalogueCreatedAt(), companion.getCatalogueUpdatedAt(), ProjectInitialization.none());
    }

    /**
     * For listings, which never touch local state and therefore know nothing about locally owned
     * fields.
     */
    public static ProjectView ofCatalogueOnly(CatalogueProjectDto dto) {
        return new ProjectView(uuid(dto.id(), "project id"), uuid(dto.orgId(), "organization id"),
                dto.name(), dto.slug(), dto.status(), null, false,
                dto.createdAtInstant(), dto.updatedAtInstant(), ProjectInitialization.none());
    }

    /** The same Project with what initialization chose; {@code initialized} follows its {@code initializedAt}. */
    public ProjectView withInitialization(ProjectInitialization chosen) {
        return new ProjectView(id, organizationId, name, slug, status, localDescription,
                initialized || chosen.initializedAt() != null, createdAt, updatedAt, chosen);
    }

    private static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException unusable) {
            throw new CatalogueBadResponseException(
                    "The Catalogue " + field + " is missing or not a UUID", unusable);
        }
    }
}
