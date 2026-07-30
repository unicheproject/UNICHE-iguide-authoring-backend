package eu.uniche.iguide.platform.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The Catalogue's authorization view of the current subject.
 *
 * <p>It exists so a client can decide what to show or hide. It is not an enforcement mechanism: the
 * Catalogue still rejects any operation the subject may not perform, and every Project-scoped request
 * still performs its own access check.
 *
 * @param etag       validator for a conditional refresh
 * @param ttlSeconds how long the Catalogue considers this view current
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogueMeAuthorizationDto(String subject,
                                          boolean platformAdmin,
                                          List<String> managedOrganisations,
                                          List<ProjectMembershipView> projectMemberships,
                                          String etag,
                                          long ttlSeconds) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectMembershipView(String projectId, String orgId, String role) {
    }
}
