package eu.uniche.iguide.navigation.api;

import eu.uniche.iguide.navigation.application.NavigationUseCase;
import eu.uniche.iguide.navigation.domain.NavigationContext;
import eu.uniche.iguide.navigation.domain.NavigationItemType;
import eu.uniche.iguide.navigation.domain.NavigationTargetType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/** Request bodies of the Navigation endpoints ({@code 07} section 14). */
public final class NavigationRequests {

    private NavigationRequests() {
    }

    public record Create(NavigationContext context, String name) {
    }

    public record Update(@NotNull Long version, String name, Map<String, Object> configuration) {
    }

    /**
     * An item: {@code labels} maps a language code to its label and changes only the languages it names; a blank
     * label removes that language's. {@code version} is the item's, required on {@code PUT}.
     */
    public record Item(Long version, NavigationItemType itemType, NavigationTargetType targetResourceType,
                       UUID targetResourceId, UUID parentItemId, Map<String, String> labels) {

        NavigationUseCase.ItemInput input() {
            return new NavigationUseCase.ItemInput(itemType, targetResourceType, targetResourceId, parentItemId, labels);
        }
    }

    public record Label(@NotNull Long version, String label) {
    }
}
