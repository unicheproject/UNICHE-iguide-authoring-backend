package eu.uniche.iguide.platform.projectaccess;

import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.util.UUID;

/**
 * The single entry point to "may this subject work in this Project".
 *
 * <p>Implementations must ask the Catalogue. Local data is never an answer, not even a cached one that
 * looks recent, and never a fallback when the Catalogue cannot be reached.
 */
public interface ProjectAccessGateway {

    /**
     * @throws eu.uniche.iguide.platform.catalogue.CatalogueNotFoundException when the Project does not
     *         exist or this subject may not see it — the two are not distinguished
     */
    ProjectContext requireAccess(UUID projectId, UnichePrincipal principal);

    /** The same check, returning the Catalogue-owned view for endpoints that must render it. */
    AccessibleProject requireAccessibleProject(UUID projectId, UnichePrincipal principal);
}
