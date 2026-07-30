package eu.uniche.iguide.platform.projectaccess.api;

import eu.uniche.iguide.platform.catalogue.CatalogueMeAuthorizationDto;
import java.util.List;

/**
 * This API's shape for the current subject's authorization view.
 *
 * <p>Defined separately from the Catalogue's wire DTO even though the fields currently match, so this
 * contract does not shift when the upstream one does. The upstream spells the field
 * {@code managedOrganisations}; this API uses {@code managedOrganizations}, consistently with its own
 * paths.
 */
public record MeAuthorizationResponse(String subject,
                                      boolean platformAdmin,
                                      List<String> managedOrganizations,
                                      List<ProjectMembership> projectMemberships) {

    public record ProjectMembership(String projectId, String organizationId, String role) {
    }

    public static MeAuthorizationResponse from(CatalogueMeAuthorizationDto view) {
        List<String> organizations = view.managedOrganisations() == null
                ? List.of()
                : List.copyOf(view.managedOrganisations());
        List<ProjectMembership> memberships = view.projectMemberships() == null
                ? List.of()
                : view.projectMemberships().stream()
                        .map(membership -> new ProjectMembership(
                                membership.projectId(), membership.orgId(), membership.role()))
                        .toList();
        return new MeAuthorizationResponse(
                view.subject(), view.platformAdmin(), organizations, memberships);
    }
}
