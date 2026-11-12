package eu.uniche.iguide.template.application;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The typed theme values a style preset sets together and a Template's {@code defaultTheme} holds ({@code 04}
 * sections 6 and 9): two font keys, four {@code #RRGGBB} colors, {@code cornerRadius}, and {@code density}, as
 * their wire names. A {@code null} value is unset.
 */
public record StyleValues(String headingFont,
                          String bodyFont,
                          String primaryColor,
                          String secondaryColor,
                          String backgroundColor,
                          String textColor,
                          String cornerRadius,
                          String density) {

    public static final List<String> FIELDS = List.of("headingFont", "bodyFont", "primaryColor", "secondaryColor",
            "backgroundColor", "textColor", "cornerRadius", "density");

    public static StyleValues fromMap(Map<String, String> values) {
        Map<String, String> v = values == null ? Map.of() : values;
        return new StyleValues(v.get("headingFont"), v.get("bodyFont"), v.get("primaryColor"),
                v.get("secondaryColor"), v.get("backgroundColor"), v.get("textColor"), v.get("cornerRadius"),
                v.get("density"));
    }

    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("headingFont", headingFont);
        map.put("bodyFont", bodyFont);
        map.put("primaryColor", primaryColor);
        map.put("secondaryColor", secondaryColor);
        map.put("backgroundColor", backgroundColor);
        map.put("textColor", textColor);
        map.put("cornerRadius", cornerRadius);
        map.put("density", density);
        return map;
    }

    /** These values where set, {@code fallback} where not ({@code 04} section 10). */
    public StyleValues over(StyleValues fallback) {
        Map<String, String> result = new HashMap<>(fallback.asMap());
        asMap().forEach((key, value) -> {
            if (value != null) {
                result.put(key, value);
            }
        });
        return fromMap(result);
    }
}
