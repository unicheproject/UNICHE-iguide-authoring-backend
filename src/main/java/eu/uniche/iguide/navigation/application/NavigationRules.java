package eu.uniche.iguide.navigation.application;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.navigation.domain.Navigation;
import eu.uniche.iguide.navigation.domain.NavigationContext;
import eu.uniche.iguide.navigation.domain.NavigationItem;
import eu.uniche.iguide.navigation.domain.NavigationItemTranslation;
import eu.uniche.iguide.navigation.domain.NavigationItemType;
import eu.uniche.iguide.navigation.domain.NavigationTargetType;
import eu.uniche.iguide.navigation.infrastructure.NavigationItemRepository;
import eu.uniche.iguide.navigation.infrastructure.NavigationItemTranslationRepository;
import eu.uniche.iguide.navigation.infrastructure.NavigationRepository;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The rules of Navigations and their items ({@code 03} section 13, {@code 06} section 12, {@code 08} Milestone 6;
 * DECISIONS.md D-046). Every method runs inside the caller's transaction.
 */
@Component
public class NavigationRules {

    private final NavigationRepository navigations;
    private final NavigationItemRepository items;
    private final NavigationItemTranslationRepository translations;
    private final ProjectLanguageLookup languages;
    private final PlaceRepository places;
    private final PointOfInterestRepository pois;
    private final RouteRepository routes;
    private final GuideRepository guides;
    private final EventRepository events;

    public NavigationRules(NavigationRepository navigations, NavigationItemRepository items,
                           NavigationItemTranslationRepository translations, ProjectLanguageLookup languages,
                           PlaceRepository places, PointOfInterestRepository pois, RouteRepository routes,
                           GuideRepository guides, EventRepository events) {
        this.navigations = navigations;
        this.items = items;
        this.translations = translations;
        this.languages = languages;
        this.places = places;
        this.pois = pois;
        this.routes = routes;
        this.guides = guides;
        this.events = events;
    }

    public Navigation requireNavigation(UUID projectId, UUID navigationId) {
        return navigations.findByProjectIdAndId(projectId, navigationId)
                .orElseThrow(() -> new ResourceNotFoundException("No such navigation in this project."));
    }

    public NavigationItem requireItem(UUID projectId, UUID navigationId, UUID itemId) {
        return items.findByProjectIdAndId(projectId, itemId)
                .filter(item -> item.getNavigationId().equals(navigationId))
                .orElseThrow(() -> new ResourceNotFoundException("No such item in this navigation."));
    }

    /**
     * The item's own fields: what it opens and where it hangs. A FOOTER item is a flat link; a GROUP is a heading
     * of the PRIMARY tree; a PAGE item waits for Pages; a RESOURCE item opens a live Resource of the Project.
     */
    public void checkItem(UUID projectId, Navigation navigation, UUID itemId, NavigationItemType type,
                          NavigationTargetType targetType, UUID targetId, UUID parentItemId, List<ErrorItem> errors) {
        if (type == null) {
            errors.add(new ErrorItem("itemType", "REQUIRED", "Choose RESOURCE or GROUP."));
        } else if (type == NavigationItemType.PAGE) {
            errors.add(new ErrorItem("itemType", "UNSUPPORTED_TYPE", "Pages do not exist in iGuide yet."));
        } else if (type == NavigationItemType.GROUP && navigation.getContext() == NavigationContext.FOOTER) {
            errors.add(new ErrorItem("itemType", "NOT_ALLOWED", "Footer links are flat: a heading has no place there."));
        } else if (type == NavigationItemType.RESOURCE) {
            checkTarget(projectId, targetType, targetId, errors);
        }
        if (parentItemId != null) {
            if (navigation.getContext() == NavigationContext.FOOTER) {
                errors.add(new ErrorItem("parentItemId", "NOT_ALLOWED", "Footer links are flat."));
            } else {
                checkParent(projectId, navigation, itemId, parentItemId, errors);
            }
        }
    }

    private void checkTarget(UUID projectId, NavigationTargetType type, UUID targetId, List<ErrorItem> errors) {
        if (type == null) {
            errors.add(new ErrorItem("targetResourceType", "REQUIRED", "Choose what the item opens."));
            return;
        }
        if (targetId == null) {
            errors.add(new ErrorItem("targetResourceId", "REQUIRED", "Choose what the item opens."));
            return;
        }
        Optional<ResourceStatus> status = targetStatus(projectId, type, targetId);
        if (status.isEmpty()) {
            errors.add(new ErrorItem("targetResourceId", "RESOURCE_NOT_FOUND", "No such " + type.label() + " in this project."));
        } else if (status.get() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem("targetResourceId", "ARCHIVED_REFERENCE", "The " + type.label() + " is archived."));
        }
    }

    public Optional<ResourceStatus> targetStatus(UUID projectId, NavigationTargetType type, UUID targetId) {
        return switch (type) {
            case PLACE -> places.findByProjectIdAndId(projectId, targetId).map(Place::getStatus);
            case POI -> pois.findByProjectIdAndId(projectId, targetId).map(PointOfInterest::getStatus);
            case ROUTE -> routes.findByProjectIdAndId(projectId, targetId).map(Route::getStatus);
            case GUIDE -> guides.findByProjectIdAndId(projectId, targetId).map(Guide::getStatus);
            case EVENT -> events.findByProjectIdAndId(projectId, targetId).map(Event::getStatus);
        };
    }

    /** The parent is an item of the same Navigation and not the item itself or one of its descendants. */
    private void checkParent(UUID projectId, Navigation navigation, UUID itemId, UUID parentItemId,
                             List<ErrorItem> errors) {
        Map<UUID, NavigationItem> all = items.findByProjectIdAndNavigationId(projectId, navigation.getId()).stream()
                .collect(Collectors.toMap(NavigationItem::getId, Function.identity()));
        if (!all.containsKey(parentItemId)) {
            errors.add(new ErrorItem("parentItemId", "RESOURCE_NOT_FOUND", "No such item in this navigation."));
            return;
        }
        for (UUID current = parentItemId; current != null; current = all.get(current).getParentItemId()) {
            if (current.equals(itemId)) {
                errors.add(new ErrorItem("parentItemId", "CYCLIC_PARENT", "An item cannot sit under itself or its own sub-items."));
                return;
            }
        }
    }

    /**
     * Labels go to enabled languages only and stay within 255 characters; a GROUP needs one in the default language,
     * since it has no target whose title could stand in ({@code 03} section 13). {@code existing} are the labels the
     * item keeps.
     */
    public void checkLabels(UUID projectId, NavigationItemType type, Map<String, String> requested,
                            Map<String, String> existing, String prefix, List<ErrorItem> errors) {
        Set<String> enabled = languages.enabledCodes(projectId);
        requested.forEach((code, label) -> {
            if (!enabled.contains(code)) {
                errors.add(new ErrorItem(prefix + code, "UNKNOWN_LANGUAGE", "Is not a language of this project."));
            } else if (label != null && label.strip().length() > ResourceRules.MAX_NAME) {
                errors.add(new ErrorItem(prefix + code, "TOO_LONG", "At most " + ResourceRules.MAX_NAME + " characters."));
            }
        });
        if (type == NavigationItemType.GROUP) {
            String defaultLanguage = languages.requireDefaultLanguage(projectId);
            Map<String, String> merged = new HashMap<>(existing);
            merged.putAll(requested);
            if (ResourceRules.trimToNull(merged.get(defaultLanguage)) == null) {
                errors.add(new ErrorItem(prefix + defaultLanguage, "REQUIRED",
                        "A heading needs a label in the default language."));
            }
        }
    }

    /** Writes the labels of the languages given; the others keep theirs. A blank label removes it. */
    public void saveLabels(NavigationItem item, Map<String, String> requested) {
        if (requested.isEmpty()) {
            return;
        }
        Map<String, NavigationItemTranslation> current = translations
                .findByProjectIdAndNavigationItemIdIn(item.getProjectId(), List.of(item.getId())).stream()
                .collect(Collectors.toMap(NavigationItemTranslation::getLanguageCode, Function.identity()));
        List<NavigationItemTranslation> toSave = new ArrayList<>();
        requested.forEach((code, label) -> {
            NavigationItemTranslation row = current.getOrDefault(code,
                    NavigationItemTranslation.of(item.getProjectId(), item.getId(), code));
            row.write(ResourceRules.trimToNull(label));
            toSave.add(row);
        });
        translations.saveAll(toSave);
    }

    public Map<String, String> labels(UUID projectId, UUID itemId) {
        return translations.findByProjectIdAndNavigationItemIdIn(projectId, List.of(itemId)).stream()
                .filter(t -> t.getLabel() != null)
                .collect(Collectors.toMap(NavigationItemTranslation::getLanguageCode, NavigationItemTranslation::getLabel));
    }

    /** The items that share a parent, in order. */
    public List<NavigationItem> siblings(UUID projectId, UUID navigationId, UUID parentItemId) {
        return items.findByProjectIdAndNavigationId(projectId, navigationId).stream()
                .filter(item -> Objects.equals(item.getParentItemId(), parentItemId))
                .sorted(Comparator.comparingInt(NavigationItem::getSortOrder))
                .toList();
    }

    /** Positions 0 to n-1 in the given order; the order constraint is deferred to the commit. */
    public void renumber(List<NavigationItem> inOrder) {
        for (int i = 0; i < inOrder.size(); i++) {
            inOrder.get(i).moveTo(inOrder.get(i).getParentItemId(), i);
        }
        items.saveAllAndFlush(inOrder);
    }

    public NavigationView view(Navigation navigation) {
        return views(navigation.getProjectId(), List.of(navigation)).getFirst();
    }

    /** Each Navigation with its items in tree order. */
    public List<NavigationView> views(UUID projectId, List<Navigation> menus) {
        if (menus.isEmpty()) {
            return List.of();
        }
        List<NavigationItem> all = items.findByProjectIdAndNavigationIdIn(projectId,
                menus.stream().map(Navigation::getId).toList());
        Map<UUID, Map<String, String>> labels = new HashMap<>();
        if (!all.isEmpty()) {
            translations.findByProjectIdAndNavigationItemIdIn(projectId, all.stream().map(NavigationItem::getId).toList())
                    .stream().filter(t -> t.getLabel() != null)
                    .forEach(t -> labels.computeIfAbsent(t.getNavigationItemId(), k -> new HashMap<>())
                            .put(t.getLanguageCode(), t.getLabel()));
        }
        return menus.stream().map(menu -> {
            Map<UUID, List<NavigationItem>> byParent = new HashMap<>();
            all.stream().filter(item -> item.getNavigationId().equals(menu.getId()))
                    .sorted(Comparator.comparingInt(NavigationItem::getSortOrder))
                    .forEach(item -> byParent.computeIfAbsent(item.getParentItemId(), k -> new ArrayList<>()).add(item));
            List<NavigationView.Item> ordered = new ArrayList<>();
            walk(byParent, null, 0, labels, ordered);
            return new NavigationView(menu, ordered);
        }).toList();
    }

    private static void walk(Map<UUID, List<NavigationItem>> byParent, UUID parent, int depth,
                             Map<UUID, Map<String, String>> labels, List<NavigationView.Item> out) {
        for (NavigationItem item : byParent.getOrDefault(parent, List.of())) {
            out.add(new NavigationView.Item(item, depth, labels.getOrDefault(item.getId(), Map.of())));
            walk(byParent, item.getId(), depth + 1, labels, out);
        }
    }
}
