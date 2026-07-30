package eu.uniche.iguide.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eu.uniche.iguide.platform.security.AudienceValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * A local stand-in for the UNICHE realm: it publishes an OpenID configuration document and a JWKS, and
 * mints tokens signed with the matching key.
 *
 * <p>This exists so tests exercise the production security configuration exactly as written — real
 * issuer discovery, a real {@code NimbusJwtDecoder}, the real audience validator — without reaching the
 * deployed IdP and without a test-only decoder bean standing in for the real one. A security test whose
 * decoder is not the production decoder proves very little.
 */
public final class TestIdp {

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final String KEY_ID = "test-key";
    private static final HttpServer SERVER = start();

    private TestIdp() {
    }

    public static String issuer() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    public static String validToken(String subject) {
        return token(issuer(), List.of(AudienceValidator.REQUIRED_AUDIENCE), subject,
                subject + "@example.org", subject);
    }

    public static String tokenWithAudience(String subject, String audience) {
        return token(issuer(), List.of(audience), subject, subject + "@example.org", subject);
    }

    public static String tokenWithIssuer(String subject, String issuer) {
        return token(issuer, List.of(AudienceValidator.REQUIRED_AUDIENCE), subject,
                subject + "@example.org", subject);
    }

    public static String serviceAccountToken() {
        String subject = UUID.randomUUID().toString();
        return token(issuer(), List.of(AudienceValidator.REQUIRED_AUDIENCE), subject, null,
                "service-account-iguide-frontend");
    }

    private static String token(String issuer, List<String> audience, String subject, String email,
                                String preferredUsername) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .claim("email", email)
                .claim("preferred_username", preferredUsername)
                .claim("name", "Test " + subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims);
        try {
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
        } catch (JOSEException signingFailed) {
            throw new IllegalStateException(signingFailed);
        }
        return jwt.serialize();
    }

    private static HttpServer start() {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException cannotBind) {
            throw new UncheckedIOException(cannotBind);
        }
        server.createContext("/.well-known/openid-configuration",
                exchange -> respond(exchange, discoveryDocument(server)));
        server.createContext("/jwks", exchange -> respond(exchange, jwks()));
        server.start();
        return server;
    }

    private static String discoveryDocument(HttpServer server) {
        String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        return """
                {
                  "issuer": "%s",
                  "jwks_uri": "%s/jwks",
                  "subject_types_supported": ["public"],
                  "id_token_signing_alg_values_supported": ["RS256"],
                  "response_types_supported": ["code"]
                }
                """.formatted(issuer, issuer);
    }

    private static String jwks() {
        return new JWKSet(new RSAKey.Builder((RSAPublicKey) KEY_PAIR.getPublic())
                .keyID(KEY_ID)
                .build()).toString();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
