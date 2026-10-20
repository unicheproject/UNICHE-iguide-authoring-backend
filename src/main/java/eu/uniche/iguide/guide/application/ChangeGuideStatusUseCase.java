package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideBlockType;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies one lifecycle transition ({@code 03} section 1). Activation needs, in the default language,
 * the title, at least one block, and text on every TEXT block. Nothing refers to a Guide until Navigation
 * and Triggers exist, so deactivation and archival are never blocked yet.
 */
@Service
public class ChangeGuideStatusUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final GuideRules rules;
    private final GuideRepository guides;
    private final TransactionTemplate transactionTemplate;

    public ChangeGuideStatusUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                                    GuideRules rules, GuideRepository guides, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.guides = guides;
        this.transactionTemplate = transactionTemplate;
    }

    public GuideView execute(UUID projectId, UUID guideId, UnichePrincipal principal, ResourceStatus target,
                             Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Guide guide = rules.requireGuide(projectId, guideId);
            ResourceRules.requireCurrentVersion(guide.getVersion(), version, "guide");
            ResourceRules.requireTransition(guide.getStatus(), target, "guide");
            if (target == ResourceStatus.ACTIVE) {
                requireComplete(projectId, guide);
            }
            guide.changeStatus(target, principal.subject(), Instant.now());
            return rules.view(guides.saveAndFlush(guide));
        });
    }

    private void requireComplete(UUID projectId, Guide guide) {
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        GuideView view = rules.view(guide);
        List<ErrorItem> missing = new ArrayList<>();
        if (view.translations().stream().noneMatch(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())) {
            missing.add(new ErrorItem("translations." + defaultLanguage + ".title", "MISSING_REQUIRED_FIELDS",
                    "Is required in the default language."));
        }
        if (view.blocks().isEmpty()) {
            missing.add(new ErrorItem("blocks", "MISSING_REQUIRED_FIELDS", "Add at least one block."));
        }
        for (int i = 0; i < view.blocks().size(); i++) {
            BlockView block = view.blocks().get(i);
            boolean texted = block.translations().stream()
                    .anyMatch(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasText());
            if (block.block().getBlockType() == GuideBlockType.TEXT && !texted) {
                missing.add(new ErrorItem("blocks[" + i + "].translations." + defaultLanguage + ".text",
                        "MISSING_REQUIRED_FIELDS", "Text is required in the default language."));
            }
        }
        if (!missing.isEmpty()) {
            throw new ValidationFailedException("The guide is not complete.", missing);
        }
    }
}
