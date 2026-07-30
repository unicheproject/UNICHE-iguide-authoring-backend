package eu.uniche.iguide.platform.projectaccess.api;

import eu.uniche.iguide.platform.projectaccess.MeAuthorizationQuery;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeAuthorizationQuery authorizationQuery;

    public MeController(MeAuthorizationQuery authorizationQuery) {
        this.authorizationQuery = authorizationQuery;
    }

    /**
     * What the current subject may do, as the Catalogue sees it. Intended for deciding what a client
     * shows or hides; it authorizes nothing.
     */
    @GetMapping("/authorization")
    public MeAuthorizationResponse authorization() {
        String subject = CurrentPrincipal.require().subject();
        return MeAuthorizationResponse.from(authorizationQuery.forSubject(subject));
    }
}
