package eu.uniche.iguide.resource.application;

import eu.uniche.iguide.resource.domain.ResourceStatus;

/**
 * The parameters every content collection takes ({@code 07} section 14, {@code 06} section 16; DECISIONS.md
 * D-030): {@code status}, {@code q} with an optional {@code language}, {@code featured} (null for both),
 * {@code pending}, {@code sort}, and paging.
 */
public record ListParams(ResourceStatus status, String q, String language, Boolean featured, boolean pending,
                         String sort, Integer page, Integer size) {
}
