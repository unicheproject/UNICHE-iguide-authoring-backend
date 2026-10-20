package eu.uniche.iguide.resource.api;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.validation.constraints.NotNull;

/** {@code POST .../{kind}/{id}/status} ({@code 07} section 14). */
public record ChangeStatusRequest(@NotNull ResourceStatus targetStatus, @NotNull Long version) {
}
