package eu.uniche.iguide.event.domain;

/** A cancelled occurrence stays in the manifest so that visitors see it as cancelled ({@code 03} section 15). */
public enum OccurrenceStatus {
    SCHEDULED,
    CANCELLED
}
