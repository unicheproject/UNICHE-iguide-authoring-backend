package eu.uniche.iguide.platform.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void echoesAClientSuppliedRequestId() throws Exception {
        MockHttpServletResponse response = filterWith("abc-123");

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
    }

    @Test
    void generatesARequestIdWhenTheClientSuppliesNone() throws Exception {
        MockHttpServletResponse response = filterWith(null);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
        assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER))).isNotNull();
    }

    /**
     * The id is echoed into log lines and error bodies, so a value that could carry a newline or markup
     * is replaced rather than sanitized in place.
     */
    @Test
    void replacesAnUnsafeClientSuppliedRequestId() throws Exception {
        MockHttpServletResponse response = filterWith("abc\ninjected log line");

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .doesNotContain("injected")
                .doesNotContain("\n");
    }

    @Test
    void replacesAnOverlongClientSuppliedRequestId() throws Exception {
        MockHttpServletResponse response = filterWith("x".repeat(200));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).hasSizeLessThan(200);
    }

    @Test
    void exposesTheIdToTheChainAndClearsItAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "in-flight");
        FilterChain chain = (servletRequest, servletResponse) ->
                assertThat(CorrelationIdFilter.currentRequestId()).isEqualTo("in-flight");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // Servlet threads are pooled: a leaked id would mislabel the next, unrelated request.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private MockHttpServletResponse filterWith(String suppliedRequestId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (suppliedRequestId != null) {
            request.addHeader(CorrelationIdFilter.HEADER, suppliedRequestId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
