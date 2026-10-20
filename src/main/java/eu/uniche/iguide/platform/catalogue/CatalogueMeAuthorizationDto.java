package eu.uniche.iguide.platform.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The Catalogue's authorization view of the current subject, which {@code GET /api/v1/me/authorization}
 * returns as it is ({@code 02} §10). Field names follow the Catalogue, {@code managedOrganisations} and
 * {@code orgId} included, so the CMS reads the same body whether it asks the Catalogue or this backend.
 *
 * <p>It exists so a client can decide what to show or hide. It is not an enforcement mechanism: the
 * Catalogue still rejects any operation the subject may not perform, and every Project-scoped request
 * still performs its own access check.
 *
 * @param etag       the Catalogue's validator of this view, passed through
 * @param ttlSeconds how long the Catalogue considers this view current, passed through
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogueMeAuthorizationDto(String subject,
                                          boolean platformAdmin,
                                          List<String> managedOrganisations,
                                          List<ProjectMembershipView> projectMemberships,
                                          String etag,
                                          Long ttlSeconds) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectMembershipView(String projectId, String orgId, String role) {
    }
}
