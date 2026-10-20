package eu.uniche.iguide.resource.domain;

import java.util.UUID;

/**
 * One row that refers to a Resource, as {@code GET .../references} and the {@code RESOURCE_IN_USE}
 * envelope list it ({@code 07} section 14).
 *
 * @param resourceType the referrer's kind, for example {@code MAP}
 * @param resourceId   the referrer's id
 * @param title        the referrer's display title; author-side names for kinds without translations
 * @param status       the referrer's lifecycle status
 */
public record ResourceReference(String resourceType, UUID resourceId, String title, ResourceStatus status) {
}
