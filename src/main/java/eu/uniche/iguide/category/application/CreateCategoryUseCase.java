package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.category.infrastructure.CategoryTranslationRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates a DRAFT Category with its scope, its name in the default language, and optionally its parent,
 * last in the Project's order. The code is generated from the name when absent.
 */
@Service
public class CreateCategoryUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final CategoryRules rules;
    private final CategoryRepository categories;
    private final CategoryTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public CreateCategoryUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                                 CategoryRules rules, CategoryRepository categories,
                                 CategoryTranslationRepository translations, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.categories = categories;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public CategoryView execute(UUID projectId, UnichePrincipal principal, String rawName, String rawCode,
                                CategoryScope scope, UUID parentCategoryId) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            String defaultLanguage = languages.requireDefaultLanguage(projectId);
            List<ErrorItem> errors = new ArrayList<>();
            String name = CategoryRules.checkName(rawName, "name", true, errors);
            if (scope == null) {
                errors.add(new ErrorItem("scope", "REQUIRED", "Choose POI or ROUTE."));
            }
            rules.checkParent(projectId, parentCategoryId, null, scope, errors);
            String code = ResourceRules.trimToNull(rawCode);
            if (code != null) {
                rules.checkCode(projectId, code, null, errors);
            }
            ResourceRules.throwIfAny(errors, "The category is invalid.");
            if (code == null) {
                code = rules.generateCode(projectId, name);
            }
            Category category = categories.save(Category.draft(projectId, parentCategoryId, scope, code,
                    categories.findMaxSortOrder(projectId) + 1, principal.subject(), Instant.now()));
            CategoryTranslation translation = CategoryTranslation.of(projectId, category.getId(), defaultLanguage);
            translation.write(name, null);
            translations.save(translation);
            return rules.view(category);
        });
    }
}
