package eu.uniche.iguide.platform.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Requires the platform audience in every token.
 *
 * <p>Issuer validation alone would accept any token the realm ever issued, including one minted for
 * an unrelated client. The audience is what makes a token intended for this platform.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    /** Not an environment axis: one platform, one audience. */
    public static final String REQUIRED_AUDIENCE = "uniche-platform";

    private static final OAuth2Error ERROR = new OAuth2Error(
            "invalid_token",
            "The required audience " + REQUIRED_AUDIENCE + " is missing",
            null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audience = token.getAudience();
        if (audience != null && audience.contains(REQUIRED_AUDIENCE)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ERROR);
    }
}
