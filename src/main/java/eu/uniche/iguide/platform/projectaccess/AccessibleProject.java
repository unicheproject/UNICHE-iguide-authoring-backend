package eu.uniche.iguide.platform.projectaccess;

import java.time.Instant;
import java.util.UUID;

/** A Project the Catalogue has just confirmed this subject may work in. */
public record AccessibleProject(UUID id,
                               UUID organizationId,
                               String name,
                               String slug,
                               String status,
                               Instant createdAt,
                               Instant updatedAt) {
}
