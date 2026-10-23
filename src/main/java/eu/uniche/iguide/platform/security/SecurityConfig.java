package eu.uniche.iguide.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.uniche.iguide.platform.identitysnapshot.IdentityProvisioningService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * This backend is an OAuth2 Resource Server and nothing else.
 *
 * <p>It is never part of a login flow: the client obtains tokens directly from Keycloak with
 * Authorization Code plus PKCE, so this application never sees a password, an authorization code, or
 * a token endpoint. Consequently sessions are stateless, no cookie is issued, and CSRF protection is
 * disabled because there is no cookie-borne credential to protect. Every request carries
 * {@code Authorization: Bearer <jwt>}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectMapper objectMapper,
                                            IdentityProvisioningService provisioningService)
            throws Exception {
        // Both are needed. The bearer-token filter rejects an invalid token through the resource
        // server's own entry point, while a request that arrives with no token at all is rejected
        // later by the exception translation filter.
        var entryPoint = EnvelopeErrorResponder.authenticationEntryPoint(
                objectMapper, new BearerTokenAuthenticationEntryPoint());
        var accessDeniedHandler = EnvelopeErrorResponder.accessDeniedHandler(objectMapper);

        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // The container and orchestrator probe target: the one operational endpoint
                        // permitted without authentication.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Whether the schema stays reachable in production is an open decision in the
                        // specification package; until it is settled, treat public exposure of the
                        // full API schema as unintended and keep these behind authentication.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> {
                        }))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Immediately after bearer-token authentication, so it only ever sees a validated
                // token and an already populated security context.
                .addFilterAfter(new JitIdentityProvisioningFilter(provisioningService),
                        BearerTokenAuthenticationFilter.class)
                .build();
    }

    /**
     * Issuer and audience are validated together. The issuer alone would accept any token the realm
     * ever minted, including one issued for an unrelated client.
     */
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator()));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id",
                "If-None-Match"));
        // X-Working-Copy-Version: the copy's version after a translation saved into it (DECISIONS.md D-031).
        configuration.setExposedHeaders(List.of("X-Request-Id", "ETag", "Location", "X-Working-Copy-Version"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
