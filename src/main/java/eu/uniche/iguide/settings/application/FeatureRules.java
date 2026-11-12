package eu.uniche.iguide.settings.application;

import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.template.domain.Capability;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code featureConfiguration} is restricted to the applied Template's {@code capabilities} ({@code 03} section 2,
 * {@code 04} section 6): a capability the Template does not declare cannot be set, and every declared one is
 * enabled at initialization unless the request disables it; {@code CHATBOT} starts disabled while it is deferred.
 */
public final class FeatureRules {

    private FeatureRules() {
    }

    /** Every declared capability, enabled except {@code CHATBOT}. */
    public static Map<String, Boolean> defaults(List<Capability> declared) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        declared.forEach(capability -> result.put(capability.name(), capability != Capability.CHATBOT));
        return result;
    }

    /**
     * {@code base} with the requested values applied; unlisted capabilities keep their value. Errors are at
     * {@code featureConfiguration.<CAPABILITY>}.
     */
    public static Map<String, Boolean> apply(List<Capability> declared, Map<String, Boolean> base,
                                             Map<String, Boolean> requested, List<ErrorItem> errors) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        declared.forEach(capability -> result.put(capability.name(), base.getOrDefault(capability.name(), false)));
        if (requested == null) {
            return result;
        }
        requested.forEach((key, enabled) -> {
            String path = "featureConfiguration." + key;
            Capability capability = Capability.parse(key);
            if (capability == null) {
                errors.add(new ErrorItem(path, "UNKNOWN_CAPABILITY", "Is not a capability."));
            } else if (!declared.contains(capability)) {
                errors.add(new ErrorItem(path, "CAPABILITY_NOT_IN_TEMPLATE",
                        "The Project's Template does not offer this capability."));
            } else if (enabled == null) {
                errors.add(new ErrorItem(path, "REQUIRED", "Is true or false."));
            } else {
                result.put(key, enabled);
            }
        });
        return result;
    }
}
