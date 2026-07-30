package eu.uniche.iguide.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.uniche.iguide.platform.web.ErrorEnvelope;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Writes the standard error envelope for the two rejections Spring Security issues itself, inside the
 * filter chain and therefore out of reach of the controller exception handler.
 *
 * <p>Without this, a caller would meet two different error shapes depending on how far into the
 * request its failure occurred. The {@code WWW-Authenticate} header the resource server sets is
 * preserved: only the body is supplied here.
 */
public final class EnvelopeErrorResponder {

    private EnvelopeErrorResponder() {
    }

    public static AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper,
                                                                    AuthenticationEntryPoint delegate) {
        return (request, response, exception) -> {
            delegate.commence(request, response, exception);
            write(objectMapper, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                    "A valid bearer token is required.");
        };
    }

    public static AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                write(objectMapper, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.");
    }

    private static void write(ObjectMapper objectMapper,
                              HttpServletResponse response,
                              HttpStatus status,
                              String type,
                              String title) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorEnvelope.of(type, title, status.value()));
    }
}
