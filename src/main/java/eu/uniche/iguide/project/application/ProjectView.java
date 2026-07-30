package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueBadResponseException;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import eu.uniche.iguide.platform.projectaccess.AccessibleProject;
import eu.uniche.iguide.project.domain.Project;
import java.util.UUID;

/**
 * A Project as the application layer hands it upward, whether it came from a completed access check or
 * straight from a passthrough listing.
 */
public record ProjectView(UUID id,
                          UUID organizationId,
                          String name,
                          String slug,
                          String status,
                          String localDescription,
                          boolean initialized) {

    public static ProjectView of(AccessibleProject project, String localDescription,
                                 boolean initialized) {
        return new ProjectView(project.id(), project.organizationId(), project.name(),
                project.slug(), project.status(), localDescription, initialized);
    }

    public static ProjectView ofCompanion(Project companion) {
        return new ProjectView(companion.getId(), companion.getOrganizationId(),
                companion.getCatalogueName(), companion.getCatalogueSlug(),
                companion.getCatalogueStatus(), companion.getLocalDescription(),
                companion.getInitializedAt() != null);
    }

    /**
     * For listings, which never touch local state and therefore know nothing about locally owned
     * fields.
     */
    public static ProjectView ofCatalogueOnly(CatalogueProjectDto dto) {
        return new ProjectView(uuid(dto.id(), "project id"), uuid(dto.orgId(), "organization id"),
                dto.name(), dto.slug(), dto.status(), null, false);
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
