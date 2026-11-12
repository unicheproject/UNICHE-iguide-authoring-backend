package eu.uniche.iguide.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Picks the caller's language from a text keyed by language code, falling back to {@code en} ({@code 04} section
 * 4). The caller's language is the request's {@code Accept-Language}; without one it is {@code en}.
 */
public final class LocalizedText {

    public static final String FALLBACK = "en";

    private LocalizedText() {
    }

    /** For the current request; {@code null} when the text is {@code null} or empty. */
    public static String pick(Map<String, String> values) {
        return pick(values, currentRanges());
    }

    public static String pick(Map<String, String> values, List<Locale.LanguageRange> ranges) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (Locale.LanguageRange range : ranges) {
            String wanted = range.getRange().toLowerCase(Locale.ROOT);
            if (wanted.equals("*")) {
                continue;
            }
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.ROOT).equals(wanted)) {
                    return entry.getValue();
                }
            }
            String primary = wanted.split("-")[0];
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.ROOT).split("-")[0].equals(primary)) {
                    return entry.getValue();
                }
            }
        }
        String fallback = values.get(FALLBACK);
        return fallback != null ? fallback : values.values().iterator().next();
    }

    private static List<Locale.LanguageRange> currentRanges() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return List.of();
        }
        HttpServletRequest request = attributes.getRequest();
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header == null || header.isBlank()) {
            return List.of();
        }
        try {
            return Locale.LanguageRange.parse(header);
        } catch (IllegalArgumentException unreadable) {
            return List.of();
        }
    }
}
