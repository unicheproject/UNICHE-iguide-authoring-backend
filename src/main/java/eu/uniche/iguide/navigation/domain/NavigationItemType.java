package eu.uniche.iguide.navigation.domain;

/**
 * What a Navigation item is (DECISIONS.md D-046; {@code 03} section 13 names {@code itemType} without values): a
 * link to a Resource, a link to a Page once Pages exist, or a heading of the PRIMARY tree whose children form a
 * submenu.
 */
public enum NavigationItemType {
    RESOURCE,
    PAGE,
    GROUP
}
