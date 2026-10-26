package eu.uniche.iguide.platform.web;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import eu.uniche.iguide.platform.correlation.CorrelationIdFilter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;

/**
 * The single error body of this API: an RFC 7807 problem details object ({@code 07} section 15, [ARC_7.9],
 * DECISIONS.md D-034), served as {@code application/problem+json}.
 *
 * <p>{@code type} is the stable URN {@code urn:uniche:iguide:problem:<code>}, so clients match on {@code code} and
 * never parse it. A structured {@code errors} array is kept rather than a flat list of strings, because component
 * and theme validation needs per-field paths that a flat list cannot express. No member ever carries a rejected
 * value.
 *
 * @param type           {@code urn:uniche:iguide:problem:} followed by {@code code}
 * @param title          short summary of the problem type, the same for every occurrence of a code
 * @param status         the HTTP status also present on the response
 * @param detail         this occurrence in words
 * @param code           stable machine-readable classification, for example {@code VALIDATION_ERROR}
 * @param requestId      the value the logs carry and the {@code X-UNICHE-Request-Id} response header returns
 * @param errors         field-level detail, omitted entirely when there is none
 * @param currentVersion on {@code VERSION_CONFLICT} only, the version the caller must reload
 * @param references     on {@code RESOURCE_IN_USE} only, the rows that still reference the Resource
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"type", "title", "status", "detail", "code", "requestId", "errors", "currentVersion",
    "references"})
public record ErrorEnvelope(String type,
                            String title,
                            int status,
                            String detail,
                            String code,
                            String requestId,
                            List<ErrorItem> errors,
                            Long currentVersion,
                            List<?> references) {

    public static final String TYPE_PREFIX = "urn:uniche:iguide:problem:";
    public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    public static ErrorEnvelope of(String code, String detail, int status, List<ErrorItem> errors) {
        return of(code, detail, status, errors, null, List.of());
    }

    public static ErrorEnvelope of(String code, String detail, int status, List<ErrorItem> errors,
                                   Long currentVersion, List<?> references) {
        return new ErrorEnvelope(TYPE_PREFIX + code, titleOf(code), status, detail, code,
                CorrelationIdFilter.currentRequestId(), errors, currentVersion, references);
    }

    public static ErrorEnvelope of(String code, String detail, int status) {
        return of(code, detail, status, List.of());
    }

    /**
     * On {@code VERSION_CONFLICT}, who changed the Resource and when ({@code 07} section 15). Both come from the
     * latest Audit Event, which does not exist yet (GAPS.md G-023), so they are present and null.
     */
    @JsonAnyGetter
    public Map<String, Object> conflictMembers() {
        Map<String, Object> members = new LinkedHashMap<>();
        if ("VERSION_CONFLICT".equals(code)) {
            members.put("modifiedBySubjectDisplayName", null);
            members.put("modifiedAt", null);
        }
        return members;
    }

    /** "VALIDATION_ERROR" reads "Validation error": the same summary for every occurrence of a code. */
    static String titleOf(String code) {
        String words = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return words.isEmpty() ? "Error" : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
