package eu.uniche.iguide.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator();

    @Test
    void acceptsATokenCarryingThePlatformAudience() {
        assertThat(validator.validate(tokenFor(List.of("uniche-platform"))).hasErrors()).isFalse();
    }

    @Test
    void acceptsATokenCarryingThePlatformAudienceAmongOthers() {
        assertThat(validator.validate(tokenFor(List.of("account", "uniche-platform")))
                .hasErrors()).isFalse();
    }

    /** Issuer validation alone would accept this: a real token from the realm, meant for another client. */
    @Test
    void rejectsATokenForAnotherClient() {
        assertThat(validator.validate(tokenFor(List.of("some-other-client"))).hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenWithNoAudienceAtAll() {
        assertThat(validator.validate(tokenFor(List.of())).hasErrors()).isTrue();
    }

    private static Jwt tokenFor(List<String> audience) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("subject")
                .audience(audience)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(claims -> claims.putAll(Map.of()))
                .build();
    }
}
