package eu.uniche.iguide.navigation.application;

import eu.uniche.iguide.navigation.domain.NavigationItem;
import eu.uniche.iguide.navigation.domain.NavigationItemType;
import eu.uniche.iguide.navigation.domain.NavigationTargetType;
import eu.uniche.iguide.navigation.infrastructure.NavigationRepository;
import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.settings.application.CapabilityLookup;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Readiness of the menus ({@code 07} section 14, {@code 03} sections 2 and 13). Navigations are live, so they are
 * always evaluated. Paths name the item by its place in tree order, {@code items[3].…}.
 * <ul>
 *   <li>a heading without a label in the default language blocks (it has no target title to fall back on); one
 *       without a label in another language warns;
 *   <li>an item whose target is missing or archived blocks ({@code DANGLING_REFERENCE});
 *   <li>an item whose target's capability is off warns ({@code CAPABILITY_DISABLED}): the manifest drops it.
 * </ul>
 */
@Component
public class NavigationReadiness implements ReadinessCheck {

    /** The capability each kind of target needs; Places need none. */
    static final Map<NavigationTargetType, String> CAPABILITIES = Map.of(
            NavigationTargetType.POI, "POI_EXPLORATION",
            NavigationTargetType.ROUTE, "ROUTES",
            NavigationTargetType.GUIDE, "GUIDES",
            NavigationTargetType.EVENT, "EVENTS");

    private final NavigationRepository navigations;
    private final NavigationRules rules;
    private final CapabilityLookup capabilities;

    public NavigationReadiness(NavigationRepository navigations, NavigationRules rules, CapabilityLookup capabilities) {
        this.navigations = navigations;
        this.rules = rules;
        this.capabilities = capabilities;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        UUID projectId = context.projectId();
        for (NavigationView view : rules.views(projectId, navigations.findByProjectIdOrderByContextAsc(projectId))) {
            UUID id = view.navigation().getId();
            String title = view.navigation().getName();
            for (int i = 0; i < view.items().size(); i++) {
                NavigationView.Item entry = view.items().get(i);
                NavigationItem item = entry.item();
                String at = "items[" + i + "].";
                if (item.getItemType() == NavigationItemType.GROUP && context.defaultLanguage() != null) {
                    if (blank(entry.labels().get(context.defaultLanguage()))) {
                        report.block("NAVIGATION", id, title, context.defaultLanguage(),
                                at + "labels." + context.defaultLanguage(), "MISSING_DEFAULT_LANGUAGE_VALUE");
                    }
                    for (String language : context.otherLanguages()) {
                        if (blank(entry.labels().get(language))) {
                            report.warn("NAVIGATION", id, title, language, at + "labels." + language,
                                    "MISSING_TRANSLATION");
                        }
                    }
                }
                if (item.getItemType() == NavigationItemType.RESOURCE) {
                    NavigationTargetType type = item.getTargetResourceType();
                    if (context.index().dangling(type.name(), item.getTargetResourceId())) {
                        report.block("NAVIGATION", id, title, null, at + "targetResourceId", "DANGLING_REFERENCE");
                    }
                    String capability = CAPABILITIES.get(type);
                    if (capability != null && capabilities.off(projectId, capability)) {
                        report.warn("NAVIGATION", id, title, null, at + "targetResourceType", "CAPABILITY_DISABLED");
                    }
                }
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
