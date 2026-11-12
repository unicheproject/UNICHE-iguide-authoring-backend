package eu.uniche.iguide.template.domain;

/** Template version status ({@code 04} section 3); only {@code ACTIVE} initializes new Projects. */
public enum TemplateStatus {
    DRAFT,
    ACTIVE,
    DEPRECATED,
    RETIRED
}
