package eu.uniche.iguide.platform.projectaccess.api;

import eu.uniche.iguide.platform.catalogue.CatalogueMeAuthorizationDto;
import eu.uniche.iguide.platform.projectaccess.MeAuthorizationPassthroughQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeAuthorizationPassthroughQuery authorizationQuery;

    public MeController(MeAuthorizationPassthroughQuery authorizationQuery) {
        this.authorizationQuery = authorizationQuery;
    }

    /**
     * What the current subject may do, as the Catalogue sees it, in the Catalogue's own shape. Intended
     * for deciding what a client shows or hides; it authorizes nothing.
     */
    @GetMapping("/authorization")
    public CatalogueMeAuthorizationDto authorization() {
        return authorizationQuery.get();
    }
}
