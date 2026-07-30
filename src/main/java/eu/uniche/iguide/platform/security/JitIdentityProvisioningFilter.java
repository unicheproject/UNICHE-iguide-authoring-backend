package eu.uniche.iguide.platform.security;

import eu.uniche.iguide.platform.identitysnapshot.IdentityProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mirrors the authenticated subject into the local identity snapshot, once per request.
 *
 * <p>Registered immediately after the bearer-token authentication filter, so it only ever runs on an
 * already validated token and an already populated security context. It is not a Spring bean scanned
 * into the main chain; {@code SecurityConfig} places it explicitly.
 *
 * <p>Every failure is caught, logged, and swallowed. The snapshot is a best-effort mirror and must
 * never fail an otherwise valid request. This filter also sits outside the reach of the controller
 * exception handler, so handling its own failures is not optional.
 */
public class JitIdentityProvisioningFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JitIdentityProvisioningFilter.class);

    private final IdentityProvisioningService provisioningService;

    public JitIdentityProvisioningFilter(IdentityProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        CurrentPrincipal.principal().ifPresent(principal -> {
            // The platform keeps local identity rows for people only.
            if (principal.isServiceAccount()) {
                return;
            }
            try {
                provisioningService.record(principal);
            } catch (RuntimeException failure) {
                log.warn("Identity provisioning failed for subject {}; continuing",
                        principal.subject(), failure);
            }
        });
        chain.doFilter(request, response);
    }
}
