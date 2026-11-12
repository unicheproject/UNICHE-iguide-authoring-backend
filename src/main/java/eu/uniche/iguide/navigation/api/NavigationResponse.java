package eu.uniche.iguide.navigation.api;

import eu.uniche.iguide.navigation.application.NavigationView;
import eu.uniche.iguide.navigation.domain.Navigation;
import eu.uniche.iguide.navigation.domain.NavigationContext;
import eu.uniche.iguide.navigation.domain.NavigationItem;
import eu.uniche.iguide.navigation.domain.NavigationItemType;
import eu.uniche.iguide.navigation.domain.NavigationTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A Navigation with its items in tree order: each item follows its parent, siblings by {@code sortOrder}, with its
 * {@code depth} so a client can draw the tree without rebuilding it.
 */
public record NavigationResponse(UUID id, UUID projectId, String name, NavigationContext context,
                                 Map<String, Object> configuration, Instant updatedAt, String updatedBySubject,
                                 long version, List<Item> items) {

    public record Item(UUID id, UUID parentItemId, int depth, int sortOrder, NavigationItemType itemType,
                       NavigationTargetType targetResourceType, UUID targetResourceId, UUID targetPageId,
                       Map<String, String> labels, long version) {

        static Item from(NavigationView.Item view) {
            NavigationItem i = view.item();
            return new Item(i.getId(), i.getParentItemId(), view.depth(), i.getSortOrder(), i.getItemType(),
                    i.getTargetResourceType(), i.getTargetResourceId(), i.getTargetPageId(),
                    new TreeMap<>(view.labels()), i.getVersion());
        }
    }

    public static NavigationResponse from(NavigationView view) {
        Navigation n = view.navigation();
        return new NavigationResponse(n.getId(), n.getProjectId(), n.getName(), n.getContext(), n.getConfiguration(),
                n.getUpdatedAt(), n.getUpdatedBySubject(), n.getVersion(),
                view.items().stream().map(Item::from).toList());
    }
}
