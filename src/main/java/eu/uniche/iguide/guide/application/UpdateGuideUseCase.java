package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideTranslation;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.guide.infrastructure.GuideTranslationRepository;
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
 * Saves a DRAFT Guide's own fields and title/summary. An ACTIVE Guide is edited through its working copy, whose
 * save and apply reuse {@link #write} (DECISIONS.md D-027).
 */
@Service
public class UpdateGuideUseCase {

    private final ProjectAccessGateway accessGateway;
    private final GuideRules rules;
    private final GuideRepository guides;
    private final GuideTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public UpdateGuideUseCase(ProjectAccessGateway accessGateway, GuideRules rules, GuideRepository guides,
                              GuideTranslationRepository translations, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.guides = guides;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public GuideView execute(UUID projectId, UUID guideId, UnichePrincipal principal, UpdateGuideCommand command) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Guide guide = rules.requireGuide(projectId, guideId);
            ResourceRules.requireCurrentVersion(guide.getVersion(), command.version(), "guide");
            ResourceRules.requireDraft(guide.getStatus(), "guide");
            return write(projectId, guide, principal, command);
        });
    }

    /**
     * Validates and writes the Guide's own fields and translations, inside the caller's transaction, without a
     * status or version check: the DRAFT save above and the working copy of an ACTIVE Guide share it.
     */
    public GuideView write(UUID projectId, Guide guide, UnichePrincipal principal, UpdateGuideCommand command) {
        UUID guideId = guide.getId();
        List<ErrorItem> errors = new ArrayList<>();
        Integer minutes = command.estimatedReadingMinutes();
        if (minutes != null && (minutes < 1 || minutes > GuideRules.MAX_READING_MINUTES)) {
            errors.add(new ErrorItem("estimatedReadingMinutes", "OUT_OF_RANGE",
                    "Must be between 1 and " + GuideRules.MAX_READING_MINUTES + "."));
        }
        Map<String, GuideTranslationInput> requested =
                command.translations() == null ? Map.of() : command.translations();
        rules.checkLanguages(projectId, requested.keySet(), errors);
        rules.checkSlug(projectId, command.slug(), guideId, errors);
        ResourceRules.throwIfAny(errors, "The guide is invalid.");

        guide.edit(command.slug(), ResourceRules.trimToNull(command.internalName()), minutes, command.featured(),
                principal.subject(), Instant.now());
        Map<String, GuideTranslation> existing = translations.findByProjectIdAndGuideId(projectId, guideId).stream()
                .collect(Collectors.toMap(GuideTranslation::getLanguageCode, Function.identity()));
        List<GuideTranslation> toSave = new ArrayList<>();
        requested.forEach((code, input) -> {
            GuideTranslation row = existing.getOrDefault(code, GuideTranslation.of(projectId, guideId, code));
            row.write(ResourceRules.trimToNull(input.title()), ResourceRules.trimToNull(input.summary()));
            toSave.add(row);
        });
        translations.saveAll(toSave);
        return rules.view(guides.saveAndFlush(guide));
    }
}
