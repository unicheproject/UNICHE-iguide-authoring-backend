package eu.uniche.iguide.navigation.application;

import eu.uniche.iguide.event.application.EventReferenceSource;
import eu.uniche.iguide.guide.application.GuideReferenceSource;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.navigation.domain.Navigation;
import eu.uniche.iguide.navigation.domain.NavigationItem;
import eu.uniche.iguide.navigation.domain.NavigationTargetType;
import eu.uniche.iguide.navigation.infrastructure.NavigationItemRepository;
import eu.uniche.iguide.navigation.infrastructure.NavigationRepository;
import eu.uniche.iguide.place.application.PlaceReferenceSource;
import eu.uniche.iguide.poi.application.PoiReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.application.RouteReferenceSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Menu items as referrers of the Resources they open ({@code 07} section 14 {@code references}): they show in the
 * target's "Used in" and, like every other referrer, keep it from leaving visitors while the menu points at it, so a
 * menu never offers a dead entry (DECISIONS.md D-046). A Navigation is live, so the reference reads as ACTIVE.
 */
@Component
public class NavigationReferences {

    private final NavigationItemRepository items;
    private final NavigationRepository navigations;
    private final NavigationRules rules;
    private final ProjectLanguageLookup languages;

    public NavigationReferences(NavigationItemRepository items, NavigationRepository navigations,
                                NavigationRules rules, ProjectLanguageLookup languages) {
        this.items = items;
        this.navigations = navigations;
        this.rules = rules;
        this.languages = languages;
    }

    List<ResourceReference> to(UUID projectId, NavigationTargetType type, UUID targetId) {
        List<NavigationItem> found = items.findByProjectIdAndTargetResourceTypeAndTargetResourceId(projectId, type,
                targetId);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, Navigation> menus = navigations.findByProjectIdOrderByContextAsc(projectId).stream()
                .collect(Collectors.toMap(Navigation::getId, Function.identity()));
        String language = languages.enabledLanguages(projectId).isEmpty() ? null
                : languages.requireDefaultLanguage(projectId);
        return found.stream().map(item -> {
            String menu = Optional.ofNullable(menus.get(item.getNavigationId())).map(Navigation::getName).orElse("Menu");
            String label = language == null ? null : rules.labels(projectId, item.getId()).get(language);
            return new ResourceReference("NAVIGATION", item.getNavigationId(), label == null ? menu : menu + ": " + label,
                    ResourceStatus.ACTIVE);
        }).toList();
    }

    @Component
    static class ToPlace implements PlaceReferenceSource {
        private final NavigationReferences references;

        ToPlace(NavigationReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID placeId) {
            return references.to(projectId, NavigationTargetType.PLACE, placeId);
        }

        /** A menu item does not link a map or floor plan, so it never fixes the Place's environment. */
        @Override
        public boolean linksPlace(UUID projectId, UUID placeId) {
            return false;
        }
    }

    @Component
    static class ToPoi implements PoiReferenceSource {
        private final NavigationReferences references;

        ToPoi(NavigationReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID poiId) {
            return references.to(projectId, NavigationTargetType.POI, poiId);
        }
    }

    @Component
    static class ToRoute implements RouteReferenceSource {
        private final NavigationReferences references;

        ToRoute(NavigationReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID routeId) {
            return references.to(projectId, NavigationTargetType.ROUTE, routeId);
        }
    }

    @Component
    static class ToGuide implements GuideReferenceSource {
        private final NavigationReferences references;

        ToGuide(NavigationReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID guideId) {
            return references.to(projectId, NavigationTargetType.GUIDE, guideId);
        }
    }

    @Component
    static class ToEvent implements EventReferenceSource {
        private final NavigationReferences references;

        ToEvent(NavigationReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID eventId) {
            return references.to(projectId, NavigationTargetType.EVENT, eventId);
        }
    }
}
