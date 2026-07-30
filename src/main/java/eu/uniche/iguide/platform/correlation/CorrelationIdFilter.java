package eu.uniche.iguide.platform.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id, so a response, its log lines, and any audit event caused
 * by it share one identifier without a tracing library.
 *
 * <p>Registered at highest precedence, ahead of the Spring Security chain, so even Security's own
 * rejections are logged under the same id. That placement also puts it outside the reach of the
 * controller exception handler, which only sees exceptions thrown during controller execution, so
 * this filter is responsible for its own failure behavior.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;

    /**
     * A client-supplied id is echoed into log lines and error bodies, so it is accepted only in a
     * shape that cannot carry a newline, a control character, or markup.
     */
    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled. A leaked id would mislabel a later, unrelated request.
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String supplied) {
        if (supplied == null) {
            return UUID.randomUUID().toString();
        }
        String trimmed = supplied.trim();
        if (trimmed.length() > MAX_LENGTH || !ACCEPTED.matcher(trimmed).matches()) {
            return UUID.randomUUID().toString();
        }
        return trimmed;
    }

    /**
     * The id of the request being served, or {@code null} outside a request. Read from the MDC so
     * there is exactly one source for logs, response headers, error bodies, and audit events.
     */
    public static String currentRequestId() {
        return MDC.get(MDC_KEY);
    }
}
