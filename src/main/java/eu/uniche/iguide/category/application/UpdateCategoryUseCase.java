package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.category.infrastructure.CategoryTranslationRepository;
import eu.uniche.iguide.category.infrastructure.ResourceCategoryRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves a DRAFT Category. A Category in use keeps its scope ({@code 03} section 10); when the scope
 * changes, its subcategories move along so they keep sharing it.
 */
@Service
public class UpdateCategoryUseCase {

    private final ProjectAccessGateway accessGateway;
    private final CategoryRules rules;
    private final CategoryRepository categories;
    private final CategoryTranslationRepository translations;
    private final ResourceCategoryRepository bindings;
    private final TransactionTemplate transactionTemplate;

    public UpdateCategoryUseCase(ProjectAccessGateway accessGateway, CategoryRules rules, CategoryRepository categories,
                                 CategoryTranslationRepository translations, ResourceCategoryRepository bindings,
                                 TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.categories = categories;
        this.translations = translations;
        this.bindings = bindings;
        this.transactionTemplate = transactionTemplate;
    }

    public CategoryView execute(UUID projectId, UUID categoryId, UnichePrincipal principal,
                                UpdateCategoryCommand command) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Category category = rules.requireCategory(projectId, categoryId);
            ResourceRules.requireCurrentVersion(category.getVersion(), command.version(), "category");
            ResourceRules.requireDraft(category.getStatus(), "category");

            List<ErrorItem> errors = new ArrayList<>();
            if (command.scope() == null) {
                errors.add(new ErrorItem("scope", "REQUIRED", "Choose POI or ROUTE."));
            } else if (command.scope() != category.getScope()
                    && bindings.existsByProjectIdAndCategoryId(projectId, categoryId)) {
                errors.add(new ErrorItem("scope", "IN_USE",
                        "Resources are in this category; remove them before changing what it applies to."));
            }
            rules.checkParent(projectId, command.parentCategoryId(), categoryId, command.scope(), errors);
            String icon = CategoryRules.checkIcon(command.icon(), errors);
            Map<String, CategoryTranslationInput> requested =
                    command.translations() == null ? Map.of() : command.translations();
            rules.checkLanguages(projectId, requested.keySet(), errors);
            requested.forEach((code, input) ->
                    CategoryRules.checkName(input.name(), "translations." + code + ".name", false, errors));
            rules.checkCode(projectId, command.code(), categoryId, errors);
            ResourceRules.throwIfAny(errors, "The category is invalid.");

            Instant now = Instant.now();
            boolean scopeChanged = command.scope() != category.getScope();
            category.edit(command.code(), command.scope(), command.parentCategoryId(), icon, principal.subject(), now);
            if (scopeChanged) {
                List<Category> children = categories.findByProjectIdAndParentCategoryId(projectId, categoryId);
                children.forEach(child -> child.moveToScope(command.scope(), principal.subject(), now));
                categories.saveAll(children);
            }
            Map<String, CategoryTranslation> existing = translations.findByProjectIdAndCategoryId(projectId, categoryId)
                    .stream().collect(Collectors.toMap(CategoryTranslation::getLanguageCode, Function.identity()));
            List<CategoryTranslation> toSave = new ArrayList<>();
            requested.forEach((code, input) -> {
                CategoryTranslation row = existing.getOrDefault(code, CategoryTranslation.of(projectId, categoryId, code));
                row.write(ResourceRules.trimToNull(input.name()), ResourceRules.trimToNull(input.description()));
                toSave.add(row);
            });
            translations.saveAll(toSave);
            return rules.view(categories.saveAndFlush(category));
        });
    }
}
