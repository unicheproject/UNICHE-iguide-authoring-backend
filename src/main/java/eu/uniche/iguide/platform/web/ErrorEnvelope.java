package eu.uniche.iguide.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.uniche.iguide.platform.correlation.CorrelationIdFilter;
import java.util.List;

/**
 * The single error body shape of this API.
 *
 * <p>A structured {@code errors} array is kept rather than a flat list of strings, because component
 * and theme validation needs per-field paths that a flat list cannot express.
 *
 * @param type      stable machine-readable classification, for example {@code VALIDATION_ERROR}
 * @param title     short human-readable summary
 * @param status    the HTTP status also present on the response
 * @param requestId the same value the logs carry and the {@code X-Request-Id} response header
 *                  returns, so a caller reporting an error identifies its own log lines
 * @param errors    field-level detail, omitted entirely when there is none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorEnvelope(String type,
                            String title,
                            int status,
                            String requestId,
                            List<ErrorItem> errors) {

    public static ErrorEnvelope of(String type, String title, int status, List<ErrorItem> errors) {
        return new ErrorEnvelope(type, title, status, CorrelationIdFilter.currentRequestId(), errors);
    }

    public static ErrorEnvelope of(String type, String title, int status) {
        return of(type, title, status, List.of());
    }
}
