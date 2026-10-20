package eu.uniche.iguide.platform.catalogue;

import java.util.List;
import java.util.UUID;

/**
 * The Catalogue as this backend needs it.
 *
 * <p>A pure HTTP port. It knows nothing about the local database, performs no caching decision, and
 * never falls back to local data: if access cannot be checked upstream, the call fails and the caller
 * reports that honestly.
 *
 * <p>Every implementation forwards the caller's bearer token unchanged and propagates the correlation
 * header, and every method maps upstream failures to the typed exceptions in this package. That
 * mapping is client-wide by design: a method left unmapped would leak a raw HTTP client exception into
 * the catch-all handler, which would report {@code 500} and discard the real upstream status.
 */
public interface CatalogueClient {

    CatalogueProjectDto getProject(UUID projectId);

    CatalogueProjectDto createProject(UUID organizationId, CatalogueCreateProjectRequest request);

    CatalogueProjectDto updateProject(UUID projectId, CatalogueUpdateProjectRequest request);

    void deleteProject(UUID projectId);

    CatalogueProjectDto restoreProject(UUID projectId);

    List<CatalogueProjectDto> listProjectsForOrganization(UUID organizationId);

    List<CatalogueProjectDto> listDeletedProjectsForOrganization(UUID organizationId);

    CatalogueOrganizationDto getOrganization(UUID organizationId);

    /** Reads the authorization view of the current subject. */
    CatalogueMeAuthorizationDto getMeAuthorization();
}
