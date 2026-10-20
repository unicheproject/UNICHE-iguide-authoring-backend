package eu.uniche.iguide.map.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * Which Resources a map shows, per resource type and per Category, never per Resource ({@code 03}
 * section 7). Places placed on the map are always shown.
 */
public record MapVisibility(@JsonProperty("POI") TypeVisibility poi, @JsonProperty("ROUTE") TypeVisibility route) {

    public record TypeVisibility(boolean visible, List<UUID> hiddenCategoryIds) {

        public TypeVisibility {
            hiddenCategoryIds = hiddenCategoryIds == null ? List.of() : List.copyOf(hiddenCategoryIds);
        }
    }

    public static MapVisibility allVisible() {
        return new MapVisibility(new TypeVisibility(true, List.of()), new TypeVisibility(true, List.of()));
    }
}
