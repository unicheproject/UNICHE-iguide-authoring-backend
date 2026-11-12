package eu.uniche.iguide.template.domain;

/**
 * The closed capability vocabulary shared by the Template, {@code ProjectSettings.featureConfiguration}, and the
 * manifest {@code features} map ({@code 04} section 6, {@code 03} section 2). {@code CUSTOM_ENTRIES} does not exist
 * while Custom Entry Types are deferred.
 */
public enum Capability {
    PAGES,
    POI_EXPLORATION,
    ROUTES,
    GUIDES,
    MAPS,
    FLOOR_PLANS,
    EVENTS,
    SEARCH,
    OFFLINE_CONTENT,
    LOCATION_TRIGGERS,
    QR_TRIGGERS,
    FEEDBACK,
    /** ICAgent chat [ARC_10.4]; a deferred milestone, so disabled at initialization ({@code 03} section 2). */
    CHATBOT;

    public static Capability parse(String value) {
        for (Capability capability : values()) {
            if (capability.name().equals(value)) {
                return capability;
            }
        }
        return null;
    }
}
