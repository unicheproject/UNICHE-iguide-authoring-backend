package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideTranslation;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.guide.infrastructure.GuideTranslationRepository;
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

/** Creates a DRAFT Guide with its title in the default language and no blocks, last in the Project's order. */
@Service
public class CreateGuideUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final GuideRules rules;
    private final GuideRepository guides;
    private final GuideTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public CreateGuideUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages, GuideRules rules,
                              GuideRepository guides, GuideTranslationRepository translations,
                              TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.guides = guides;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public GuideView execute(UUID projectId, UnichePrincipal principal, String rawTitle, String rawSlug) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            String defaultLanguage = languages.requireDefaultLanguage(projectId);
            List<ErrorItem> errors = new ArrayList<>();
            String title = ResourceRules.trimToNull(rawTitle);
            if (title == null) {
                errors.add(new ErrorItem("title", "REQUIRED", "Is required in the default language."));
            }
            String slug = ResourceRules.trimToNull(rawSlug);
            if (slug != null) {
                rules.checkSlug(projectId, slug, null, errors);
            }
            ResourceRules.throwIfAny(errors, "The guide is invalid.");
            if (slug == null) {
                slug = rules.generateSlug(projectId, title);
            }
            Guide guide = guides.save(Guide.draft(projectId, slug, guides.findMaxSortOrder(projectId) + 1,
                    principal.subject(), Instant.now()));
            GuideTranslation translation = GuideTranslation.of(projectId, guide.getId(), defaultLanguage);
            translation.write(title, null);
            translations.save(translation);
            return rules.view(guide);
        });
    }
}
