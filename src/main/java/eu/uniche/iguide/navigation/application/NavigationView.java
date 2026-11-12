package eu.uniche.iguide.navigation.application;

import eu.uniche.iguide.navigation.domain.Navigation;
import eu.uniche.iguide.navigation.domain.NavigationItem;
import java.util.List;
import java.util.Map;

/**
 * A Navigation with its items in tree order: each item follows its parent, siblings by {@code sortOrder}, with its
 * depth (0 for a top-level item) and its labels by language.
 */
public record NavigationView(Navigation navigation, List<Item> items) {

    public record Item(NavigationItem item, int depth, Map<String, String> labels) {
    }
}
