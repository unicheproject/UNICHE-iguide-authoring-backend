package eu.uniche.iguide.platform.projectaccess;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueMeAuthorizationDto;
import org.springframework.stereotype.Service;

/**
 * Reads the Catalogue's authorization view of the current subject, on every call ({@code 02} §10).
 *
 * <p>Nothing is cached: the CMS loads the view once per session and again after a {@code 403}, so a
 * cache would save little and would serve a stale view after a membership change.
 *
 * <p>This is not an enforcement mechanism. It exists so a client can decide what to show or hide; the
 * Catalogue still rejects any operation the subject may not perform, and every Project-scoped request
 * still performs its own access check through {@link ProjectAccessGateway}.
 */
@Service
public class MeAuthorizationPassthroughQuery {

    private final CatalogueClient catalogueClient;

    public MeAuthorizationPassthroughQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public CatalogueMeAuthorizationDto get() {
        return catalogueClient.getMeAuthorization();
    }
}
