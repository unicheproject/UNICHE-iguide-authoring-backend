package eu.uniche.iguide.resource.api;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** {@code PUT .../{kind}:order}: every id of the collection, in the new order. */
public record OrderRequest(@NotNull List<UUID> ids) {
}
