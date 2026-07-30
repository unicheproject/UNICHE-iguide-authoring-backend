package eu.uniche.iguide.platform.projectaccess;

import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.util.UUID;

/**
 * Explicit, immutable Project context, produced only by a completed access check and then passed as a
 * parameter through the application layer.
 *
 * <p>Passing it explicitly rather than reading a hidden thread-local is what makes tenancy testable: a
 * service that cannot obtain context without being handed it cannot accidentally operate on the wrong
 * Project.
 */
public record ProjectContext(UUID projectId, UUID organizationId, UnichePrincipal principal) {
}
