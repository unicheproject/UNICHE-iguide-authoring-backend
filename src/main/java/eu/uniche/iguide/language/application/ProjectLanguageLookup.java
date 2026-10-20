package eu.uniche.iguide.language.application;

import eu.uniche.iguide.language.domain.ProjectLanguage;
import eu.uniche.iguide.language.infrastructure.ProjectLanguageRepository;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The enabled languages and the default language of a Project, for the Resource kinds that translate.
 * Callers have already passed the Project access check.
 */
@Component
public class ProjectLanguageLookup {

    private final ProjectLanguageRepository repository;

    public ProjectLanguageLookup(ProjectLanguageRepository repository) {
        this.repository = repository;
    }

    public List<ProjectLanguage> enabledLanguages(UUID projectId) {
        return repository.findByProjectIdAndEnabledTrueOrderBySortOrderAsc(projectId);
    }

    public Set<String> enabledCodes(UUID projectId) {
        return enabledLanguages(projectId).stream()
                .map(ProjectLanguage::getLanguageCode)
                .collect(Collectors.toSet());
    }

    /**
     * The default language code. A Project without one cannot hold translated content yet: the default
     * language is mandatory ({@code 03} section 3).
     */
    public String requireDefaultLanguage(UUID projectId) {
        return enabledLanguages(projectId).stream()
                .filter(ProjectLanguage::isDefaultLanguage)
                .map(ProjectLanguage::getLanguageCode)
                .findFirst()
                .orElseThrow(() -> new ValidationFailedException("The Project has no default language yet.",
                        List.of(new ErrorItem("languages", "NO_PROJECT_LANGUAGES",
                                "Add a default language to the Project first."))));
    }
}
