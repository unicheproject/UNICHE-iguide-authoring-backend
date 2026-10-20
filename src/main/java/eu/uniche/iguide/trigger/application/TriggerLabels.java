package eu.uniche.iguide.trigger.application;

import eu.uniche.iguide.trigger.domain.InteractionTrigger;

/** A trigger as a where-used list names it: its type and its identifying value. */
public final class TriggerLabels {

    private TriggerLabels() {
    }

    public static String of(InteractionTrigger trigger) {
        var c = trigger.getConfiguration();
        return switch (trigger.getTriggerType()) {
            case QR_CODE -> "QR code " + trigger.getCode();
            case BEACON -> "Bluetooth beacon " + c.get("uuid") + " / " + c.get("major") + " / " + c.get("minor");
            case GEO_PROXIMITY -> "Geo-proximity " + c.get("radiusMeters") + " m";
            default -> trigger.getTriggerType().name();
        };
    }
}
