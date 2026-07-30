package eu.uniche.iguide.platform.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated subject, as far as this backend is entitled to know it.
 *
 * <p>A valid token proves identity only. Nothing here says what the subject may do: the Catalogue
 * decides that, per request.
 *
 * <p>The raw bearer token is retained in request scope so it can be forwarded unchanged to the
 * Catalogue. It must never be logged, persisted, or copied into an audit event or an error body.
 */
public record UnichePrincipal(String issuer,
                              String subject,
                              String email,
                              String preferredUsername,
                              String displayName,
                              String rawBearerToken) {

    /** Keycloak issues these for machine clients; the platform keeps local identity rows for people. */
    public boolean isServiceAccount() {
        return preferredUsername != null && preferredUsername.startsWith("service-account-");
    }

    public static UnichePrincipal from(Jwt jwt) {
        return new UnichePrincipal(
                jwt.getIssuer() == null ? null : jwt.getIssuer().toString(),
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"),
                jwt.getTokenValue());
    }

    /** Deliberately overridden so the bearer token cannot reach a log line through a lazy format. */
    @Override
    public String toString() {
        return "UnichePrincipal[subject=" + subject + ", issuer=" + issuer + "]";
    }
}
