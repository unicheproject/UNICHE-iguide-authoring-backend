package eu.uniche.iguide.resource.application;

import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;

/**
 * Filters and orders the content collections share ({@code 07} section 14, {@code 06} section 16; DECISIONS.md
 * D-030). Error messages never repeat the rejected value.
 */
public final class ListFilters {

    private ListFilters() {
    }

    /** The {@code ILIKE} pattern of a search term, or {@code null} when there is no term. */
    public static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                + "%";
    }

    /**
     * Whether a translation row of the Resource matches the pattern on {@code textAttribute}, in any language or only
     * in {@code language} when given.
     */
    public static Predicate translationMatches(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, UUID projectId,
                                               String pattern, String language, Class<?> translationType,
                                               String ownerAttribute, String textAttribute) {
        Subquery<UUID> matched = query.subquery(UUID.class);
        var t = matched.from(translationType);
        List<Predicate> where = new ArrayList<>(List.of(
                cb.equal(t.get("projectId"), projectId),
                cb.equal(t.get(ownerAttribute), root.get("id")),
                cb.like(cb.lower(t.get(textAttribute)), pattern, '\\')));
        if (language != null) {
            where.add(cb.equal(t.get("languageCode"), language));
        }
        matched.select(t.get(ownerAttribute)).where(where.toArray(Predicate[]::new));
        return cb.exists(matched);
    }

    /** The trimmed language, which must be enabled in the Project; {@code null} when absent. */
    public static String requireLanguage(String language, Set<String> enabledCodes) {
        String code = ResourceRules.trimToNull(language);
        if (code != null && !enabledCodes.contains(code)) {
            throw new ValidationFailedException("The language is not one of this project.",
                    List.of(new ErrorItem("language", "UNKNOWN_LANGUAGE", "Is not a language of this project.")));
        }
        return code;
    }

    /**
     * {@code sort=<field>} or {@code <field>,asc|desc} among the allowed, indexed fields, else {@code sortOrder asc};
     * {@code createdAt} breaks ties either way.
     */
    public static Sort sort(String raw, Set<String> allowed) {
        String value = ResourceRules.trimToNull(raw);
        if (value == null) {
            return Sort.by("sortOrder", "createdAt");
        }
        String[] parts = value.split(",", -1);
        Sort.Direction direction = parts.length > 1 ? Sort.Direction.fromOptionalString(parts[1].strip()).orElse(null)
                : Sort.Direction.ASC;
        if (parts.length > 2 || direction == null || !allowed.contains(parts[0].strip())) {
            throw new ValidationFailedException("The sort is not supported.", List.of(new ErrorItem("sort",
                    "INVALID_SORT", "Sort by one of " + String.join(", ", allowed.stream().sorted().toList())
                    + ", optionally followed by ,asc or ,desc.")));
        }
        return Sort.by(direction, parts[0].strip()).and(Sort.by("createdAt"));
    }
}
