package eu.uniche.iguide.platform.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Reads the validated token of the request being served.
 *
 * <p>This is a convenience over the security context, not a substitute for passing context
 * explicitly. Project-owned service methods still take {@code ProjectContext} as a parameter, so
 * service boundaries stay testable and no tenancy decision depends on a hidden thread-local.
 */
public final class CurrentPrincipal {

    private CurrentPrincipal() {
    }

    public static Optional<Jwt> jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }

    public static Optional<UnichePrincipal> principal() {
        return jwt().map(UnichePrincipal::from);
    }

    public static Optional<String> subject() {
        return jwt().map(Jwt::getSubject);
    }

    /**
     * The principal, or an {@link IllegalStateException} if there is none. Every path reaching this
     * has already passed an authenticated endpoint, so an empty context is a wiring defect rather
     * than an authentication failure, and is reported as {@code 500} rather than {@code 401}.
     */
    public static UnichePrincipal require() {
        return principal().orElseThrow(
                () -> new IllegalStateException("No validated JWT in the security context"));
    }
}
