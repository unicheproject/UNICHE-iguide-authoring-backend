package eu.uniche.iguide.navigation.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.navigation.domain.Navigation;
import eu.uniche.iguide.navigation.domain.NavigationContext;
import eu.uniche.iguide.navigation.domain.NavigationItem;
import eu.uniche.iguide.navigation.domain.NavigationItemType;
import eu.uniche.iguide.navigation.domain.NavigationTargetType;
import eu.uniche.iguide.navigation.infrastructure.NavigationItemRepository;
import eu.uniche.iguide.navigation.infrastructure.NavigationRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.settings.application.ContentRevision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes the Navigations of a Project and their items ({@code 03} section 13, {@code 07} section 14). A Navigation
 * has no lifecycle and no working copy: every write is live, raises {@code contentRevision} in the same transaction,
 * and counts as a change of its Navigation (DECISIONS.md D-046). Items and Navigations carry versions, so a stale
 * edit is a {@code 409 VERSION_CONFLICT}; reordering takes the complete list instead.
 */
@Service
public class NavigationUseCase {

    private final ProjectAccessGateway accessGateway;
    private final NavigationRules rules;
    private final NavigationRepository navigations;
    private final NavigationItemRepository items;
    private final ProjectLanguageLookup languages;
    private final ContentRevision contentRevision;
    private final TransactionTemplate transactionTemplate;

    public NavigationUseCase(ProjectAccessGateway accessGateway, NavigationRules rules,
                             NavigationRepository navigations, NavigationItemRepository items,
                             ProjectLanguageLookup languages, ContentRevision contentRevision,
                             TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.navigations = navigations;
        this.items = items;
        this.languages = languages;
        this.contentRevision = contentRevision;
        this.transactionTemplate = transactionTemplate;
    }

    /** What an item says: its type, its target, and its labels by language (only those to change). */
    public record ItemInput(NavigationItemType itemType, NavigationTargetType targetResourceType,
                            UUID targetResourceId, UUID parentItemId, Map<String, String> labels) {

        Map<String, String> labelsOrEmpty() {
            return labels == null ? Map.of() : labels;
        }
    }

    /** {@code POST .../navigations}: the Project's PRIMARY or FOOTER Navigation, at most one of each. */
    public NavigationView create(UUID projectId, UnichePrincipal principal, NavigationContext context, String name) {
        accessGateway.requireAccess(projectId, principal);
        return write(projectId, () -> {
            List<ErrorItem> errors = new ArrayList<>();
            if (context == null) {
                errors.add(new ErrorItem("context", "REQUIRED", "Choose PRIMARY or FOOTER."));
            }
            ResourceRules.requireName(name, "name", errors);
            ResourceRules.throwIfAny(errors, "The navigation is invalid.");
            if (navigations.existsByProjectIdAndContext(projectId, context)) {
                throw new DuplicateValueException("context", "This project already has a " + context + " navigation.");
            }
            return rules.view(navigations.saveAndFlush(Navigation.of(projectId, context, name.strip(),
                    principal.subject(), Instant.now())));
        });
    }

    public NavigationView update(UUID projectId, UUID navigationId, UnichePrincipal principal, Long version,
                                 String name, Map<String, Object> configuration) {
        accessGateway.requireAccess(projectId, principal);
        return write(projectId, () -> {
            Navigation navigation = rules.requireNavigation(projectId, navigationId);
            ResourceRules.requireCurrentVersion(navigation.getVersion(), version, "navigation");
            List<ErrorItem> errors = new ArrayList<>();
            ResourceRules.requireName(name, "name", errors);
            ResourceRules.throwIfAny(errors, "The navigation is invalid.");
            navigation.edit(name.strip(), configuration, principal.subject(), Instant.now());
            return rules.view(navigations.saveAndFlush(navigation));
        });
    }

    /** Adds an item last among its siblings. */
    public NavigationView addItem(UUID projectId, UUID navigationId, UnichePrincipal principal, ItemInput input) {
        accessGateway.requireAccess(projectId, principal);
        return write(projectId, () -> {
            Navigation navigation = rules.requireNavigation(projectId, navigationId);
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkItem(projectId, navigation, null, input.itemType(), input.targetResourceType(),
                    input.targetResourceId(), input.parentItemId(), errors);
            rules.checkLabels(projectId, input.itemType(), input.labelsOrEmpty(), Map.of(), "labels.", errors);
            ResourceRules.throwIfAny(errors, "The item is invalid.");
            NavigationItem item = NavigationItem.of(projectId, navigationId, input.parentItemId(),
                    rules.siblings(projectId, navigationId, input.parentItemId()).size());
            item.target(input.itemType(), input.targetResourceType(), input.targetResourceId());
            item = items.saveAndFlush(item);
            rules.saveLabels(item, input.labelsOrEmpty());
            return touched(navigation, principal);
        });
    }

    /**
     * Changes an item: its type, target, labels of the languages given, and its parent. A new parent takes it last
     * among its children and closes the gap it leaves.
     */
    public NavigationView updateItem(UUID projectId, UUID navigationId, UUID itemId, UnichePrincipal principal,
                                     Long version, ItemInput input) {
        accessGateway.requireAccess(projectId, principal);
        return write(projectId, () -> {
            Navigation navigation = rules.requireNavigation(projectId, navigationId);
            NavigationItem item = rules.requireItem(projectId, navigationId, itemId);
            ResourceRules.requireCurrentVersion(item.getVersion(), version, "navigation item");
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkItem(projectId, navigation, itemId, input.itemType(), input.targetResourceType(),
                    input.targetResourceId(), input.parentItemId(), errors);
            rules.checkLabels(projectId, input.itemType(), input.labelsOrEmpty(), rules.labels(projectId, itemId),
                    "labels.", errors);
            ResourceRules.throwIfAny(errors, "The item is invalid.");
            UUID formerParent = item.getParentItemId();
            item.target(input.itemType(), input.targetResourceType(), input.targetResourceId());
            if (!Objects.equals(formerParent, input.parentItemId())) {
                item.moveTo(input.parentItemId(), rules.siblings(projectId, navigationId, input.parentItemId()).size());
                items.saveAndFlush(item);
                rules.renumber(rules.siblings(projectId, navigationId, formerParent));
            } else {
                items.saveAndFlush(item);
            }
            rules.saveLabels(item, input.labelsOrEmpty());
            return touched(navigation, principal);
        });
    }

    /** Removes an item with every item under it, and closes the gap among its siblings. */
    public NavigationView removeItem(UUID projectId, UUID navigationId, UUID itemId, UnichePrincipal principal,
                                     Long version) {
        accessGateway.requireAccess(projectId, principal);
        return write(projectId, () -> {
            Navigation navigation = rules.requireNavigation(projectId, navigationId);
            NavigationItem item = rules.requireItem(projectId, navigationId, itemId);
            ResourceRules.requireCurrentVersion(item.getVersion(), version, "navigation item");
            UUID parent = item.getParentItemId();
            items.delete(item);
            items.flush();
            rules.renumber(rules.siblings(projectId, navigationId, parent));
            return touched(navigation, principal);
        });
    }

    /** {@code PUT .../items/{itemId}/translations/{lang}}: one language's label; blank removes it. */
    public NavigationView putLabel(UUID projectId, UUID navigationId, UUID itemId, UnichePrincipal principal,
                                   String language, Long version, String label) {
        accessGateway.requireAccess(projectId, principal);
        return write(projectId, () -> {
            Navigation navigation = rules.requireNavigation(projectId, navigationId);
            NavigationItem item = rules.requireItem(projectId, navigationId, itemId);
            ResourceRules.requireCurrentVersion(item.getVersion(), version, "navigation item");
            if (language == null || !languages.enabledCodes(projectId).contains(language)) {
                throw new ValidationFailedException("The language is not enabled.", List.of(new ErrorItem("language",
                        "UNKNOWN_LANGUAGE", "Is not a language of this project.")));
            }
            Map<String, String> requested = new HashMap<>();
            requested.put(language, label);
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkLabels(projectId, item.getItemType(), requested, rules.labels(projectId, itemId), "", errors);
            List<ErrorItem> atLabel = errors.stream()
                    .map(e -> new ErrorItem("label", e.code(), e.message())).toList();
            ResourceRules.throwIfAny(atLabel, "The label is invalid.");
            rules.saveLabels(item, requested);
            return touched(navigation, principal);
        });
    }

    /**
     * {@code PUT .../items:order}: every item of the Navigation exactly once, in tree order. Each item takes the
     * next position among the items that share its parent; parents do not change here.
     */
    public NavigationView reorder(UUID projectId, UUID navigationId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        return write(projectId, () -> {
            Navigation navigation = rules.requireNavigation(projectId, navigationId);
            Map<UUID, NavigationItem> byId = items.findByProjectIdAndNavigationId(projectId, navigationId).stream()
                    .collect(Collectors.toMap(NavigationItem::getId, Function.identity()));
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != byId.size() || !new HashSet<>(ids).equals(byId.keySet())) {
                throw new ValidationFailedException("The order must list every item of the navigation exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER", "List every item of this navigation exactly once.")));
            }
            Map<UUID, Integer> next = new LinkedHashMap<>();
            List<NavigationItem> ordered = new ArrayList<>();
            for (UUID id : ids) {
                NavigationItem item = byId.get(id);
                int position = next.merge(item.getParentItemId() == null ? navigationId : item.getParentItemId(), 1,
                        Integer::sum) - 1;
                item.moveTo(item.getParentItemId(), position);
                ordered.add(item);
            }
            items.saveAllAndFlush(ordered);
            return touched(navigation, principal);
        });
    }

    private NavigationView touched(Navigation navigation, UnichePrincipal principal) {
        navigation.touch(principal.subject(), Instant.now());
        return rules.view(navigations.saveAndFlush(navigation));
    }

    /** One live write: the change and the {@code contentRevision} step commit together. */
    private NavigationView write(UUID projectId, Supplier<NavigationView> change) {
        return transactionTemplate.execute(status -> {
            NavigationView result = change.get();
            contentRevision.increment(projectId);
            return result;
        });
    }
}
