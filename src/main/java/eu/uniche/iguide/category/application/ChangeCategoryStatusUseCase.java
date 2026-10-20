package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceInUseException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies one lifecycle transition ({@code 03} section 1). Activation needs the default-language name and
 * an ACTIVE parent; restoring needs a parent that is not archived. Deactivation and archival are refused
 * with {@code RESOURCE_IN_USE} while subcategories or Points of Interest or Routes are in the Category.
 */
@Service
public class ChangeCategoryStatusUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final CategoryRules rules;
    private final CategoryRepository categories;
    private final TransactionTemplate transactionTemplate;

    public ChangeCategoryStatusUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                                       CategoryRules rules, CategoryRepository categories,
                                       TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.categories = categories;
        this.transactionTemplate = transactionTemplate;
    }

    public CategoryView execute(UUID projectId, UUID categoryId, UnichePrincipal principal, ResourceStatus target,
                                Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Category category = rules.requireCategory(projectId, categoryId);
            ResourceRules.requireCurrentVersion(category.getVersion(), version, "category");
            ResourceRules.requireTransition(category.getStatus(), target, "category");
            Optional<Category> parent = category.getParentCategoryId() == null ? Optional.empty()
                    : categories.findByProjectIdAndId(projectId, category.getParentCategoryId());
            if (target == ResourceStatus.ACTIVE) {
                String defaultLanguage = languages.requireDefaultLanguage(projectId);
                List<ErrorItem> missing = new ArrayList<>();
                boolean named = rules.view(category).translations().stream()
                        .anyMatch(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasName());
                if (!named) {
                    missing.add(new ErrorItem("translations." + defaultLanguage + ".name", "MISSING_REQUIRED_FIELDS",
                            "Is required in the default language."));
                }
                if (parent.isPresent() && parent.get().getStatus() != ResourceStatus.ACTIVE) {
                    missing.add(new ErrorItem("parentCategoryId", "MISSING_REQUIRED_FIELDS",
                            "Activate the parent category first."));
                }
                if (!missing.isEmpty()) {
                    throw new ValidationFailedException("The category is not complete.", missing);
                }
            }
            if (category.getStatus() == ResourceStatus.ARCHIVED && parent.isPresent()
                    && parent.get().getStatus() == ResourceStatus.ARCHIVED) {
                throw new ValidationFailedException("The parent category is archived.", List.of(new ErrorItem(
                        "parentCategoryId", "ILLEGAL_TRANSITION", "Restore the parent category first.")));
            }
            if (category.getStatus().leavesVisitorsFor(target)) {
                List<ResourceReference> references = rules.referencesTo(projectId, category);
                if (!references.isEmpty()) {
                    throw new ResourceInUseException("The category is still used.", references);
                }
            }
            category.changeStatus(target, principal.subject(), Instant.now());
            return rules.view(categories.saveAndFlush(category));
        });
    }
}
