package eu.uniche.iguide.template.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Theme colors ({@code 04} section 11): {@code #RRGGBB}, stored upper case, and the WCAG 2.1 contrast rules — text
 * on background at least 4.5:1, primary and secondary on background at least 3:1 ({@code 14} section 2) [ARC_4.3].
 */
public final class Colors {

    public static final double TEXT_MINIMUM = 4.5;
    public static final double COMPONENT_MINIMUM = 3.0;

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /** One color pair below its threshold, named by the field that fails. */
    public record ContrastFailure(String field, double ratio, double minimum) {
    }

    private Colors() {
    }

    public static boolean valid(String color) {
        return color != null && HEX.matcher(color).matches();
    }

    /** Upper case, or {@code null} when not a {@code #RRGGBB} color. */
    public static String normalize(String color) {
        if (color == null) {
            return null;
        }
        return valid(color.strip()) ? color.strip().toUpperCase(Locale.ROOT) : null;
    }

    /** The WCAG 2.1 contrast ratio of two {@code #RRGGBB} colors, from 1 to 21. */
    public static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /** Rounded down to two decimals, so a shown ratio never passes where the exact one fails. */
    public static double display(double ratio) {
        return Math.floor(ratio * 100) / 100;
    }

    /** The failing pairs of fully set, valid values; a pair with an unset or invalid color is not checked. */
    public static List<ContrastFailure> contrastFailures(StyleValues values) {
        List<ContrastFailure> failures = new ArrayList<>();
        String background = values.backgroundColor();
        if (!valid(background)) {
            return failures;
        }
        check("textColor", values.textColor(), background, TEXT_MINIMUM, failures);
        check("primaryColor", values.primaryColor(), background, COMPONENT_MINIMUM, failures);
        check("secondaryColor", values.secondaryColor(), background, COMPONENT_MINIMUM, failures);
        return failures;
    }

    private static void check(String field, String color, String background, double minimum,
                              List<ContrastFailure> failures) {
        if (valid(color)) {
            double ratio = contrast(color, background);
            if (ratio < minimum) {
                failures.add(new ContrastFailure(field, ratio, minimum));
            }
        }
    }

    private static double luminance(String color) {
        int rgb = Integer.parseInt(color.substring(1), 16);
        return 0.2126 * channel((rgb >> 16) & 0xFF) + 0.7152 * channel((rgb >> 8) & 0xFF)
                + 0.0722 * channel(rgb & 0xFF);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
