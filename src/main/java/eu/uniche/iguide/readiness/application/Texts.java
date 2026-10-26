package eu.uniche.iguide.readiness.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Translation rows as the text rules of {@link ReadinessReport} read them: owner → language → field → text. */
public final class Texts {

    private Texts() {
    }

    /** {@code fields} turns one row into its fields; {@code null} texts are allowed. */
    public static <T> Map<UUID, Map<String, Map<String, String>>> byOwner(List<T> rows, Function<T, UUID> owner,
                                                                         Function<T, String> language,
                                                                         Function<T, Map<String, String>> fields) {
        Map<UUID, Map<String, Map<String, String>>> result = new HashMap<>();
        for (T row : rows) {
            result.computeIfAbsent(owner.apply(row), id -> new HashMap<>()).put(language.apply(row), fields.apply(row));
        }
        return result;
    }

    /** Field name, text, field name, text, …, null texts kept. */
    public static Map<String, String> of(String... namesAndTexts) {
        Map<String, String> fields = new HashMap<>();
        for (int i = 0; i + 1 < namesAndTexts.length; i += 2) {
            fields.put(namesAndTexts[i], namesAndTexts[i + 1]);
        }
        return fields;
    }
}
