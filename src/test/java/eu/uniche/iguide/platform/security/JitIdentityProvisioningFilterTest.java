package eu.uniche.iguide.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.uniche.iguide.platform.identitysnapshot.IdentityProvisioningService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JitIdentityProvisioningFilterTest {

    private final IdentityProvisioningService provisioningService =
            mock(IdentityProvisioningService.class);
    private final JitIdentityProvisioningFilter filter =
            new JitIdentityProvisioningFilter(provisioningService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mirrorsTheAuthenticatedSubject() throws Exception {
        authenticate("someone");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new MockFilterChain());

        verify(provisioningService, times(1)).record(any());
    }

    /** The platform keeps local identity rows for people only. */
    @Test
    void skipsServiceAccounts() throws Exception {
        authenticate("service-account-iguide-frontend");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new MockFilterChain());

        verify(provisioningService, never()).record(any());
    }

    @Test
    void doesNothingWhenThereIsNoAuthenticatedSubject() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new MockFilterChain());

        verify(provisioningService, never()).record(any());
    }

    /**
     * The snapshot is a best-effort mirror. A failure here must never fail an otherwise valid request,
     * and this filter sits outside the reach of the controller exception handler, so it has to absorb its
     * own failures.
     */
    @Test
    void aFailingProvisioningStepDoesNotFailTheRequest() throws Exception {
        authenticate("someone");
        when(provisioningService.record(any()))
                .thenThrow(new IllegalStateException("the database is on fire"));

        boolean[] chainWasCalled = {false};
        FilterChain chain = (request, response) -> chainWasCalled[0] = true;

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(chainWasCalled[0]).isTrue();
    }

    private static void authenticate(String preferredUsername) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("token")
                        .header("alg", "RS256")
                        .subject("subject-1")
                        .claim("preferred_username", preferredUsername)
                        .build()));
    }
}
