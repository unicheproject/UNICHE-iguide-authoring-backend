package eu.uniche.iguide.language.application;

import eu.uniche.iguide.language.domain.ProjectLanguage;
import eu.uniche.iguide.language.infrastructure.ProjectLanguageRepository;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The rules and the write behind every change of a Project's languages ({@code 03} section 3), shared by
 * {@code PUT .../languages} and Project initialization ({@code 04} section 8 step 6). Callers have passed the Project
 * access check and run {@link #replace} inside their transaction.
 *
 * <ul>
 *   <li>exactly one language is the default;
 *   <li>a language left out is disabled, never deleted, so its translations come back with it;
 *   <li>changing the default requires every non-archived Resource to have a title in the new default.
 * </ul>
 */
@Component
public class ProjectLanguagesWriter {

    public static final Pattern CODE = Pattern.compile("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$");
    private static final int MAX_DISPLAY_NAME = 100;

    private final ProjectLanguageRepository repository;
    private final ProjectLanguageLookup lookup;
    private final ProjectLanguagesLoader loader;

    public ProjectLanguagesWriter(ProjectLanguageRepository repository, ProjectLanguageLookup lookup,
                                  ProjectLanguagesLoader loader) {
        this.repository = repository;
        this.lookup = lookup;
        this.loader = loader;
    }

    /** Adds an error per broken rule; paths are {@code languages} and {@code languages[i].field}. */
    public static void validate(List<LanguageInput> requested, List<ErrorItem> errors) {
        if (requested == null || requested.isEmpty()) {
            errors.add(new ErrorItem("languages", "REQUIRED", "Add at least one language."));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < requested.size(); i++) {
            LanguageInput language = requested.get(i);
            String path = "languages[" + i + "]";
            if (language.languageCode() == null || !CODE.matcher(language.languageCode()).matches()) {
                errors.add(new ErrorItem(path + ".languageCode", "INVALID_FORMAT",
                        "Use a language code such as el, en, or pt-BR."));
            } else if (!seen.add(language.languageCode())) {
                errors.add(new ErrorItem(path + ".languageCode", "DUPLICATE_VALUE", "The language is listed twice."));
            }
            String displayName = ResourceRules.trimToNull(language.displayName());
            if (displayName == null) {
                errors.add(new ErrorItem(path + ".displayName", "REQUIRED", "Is required."));
            } else if (displayName.length() > MAX_DISPLAY_NAME) {
                errors.add(new ErrorItem(path + ".displayName", "TOO_LONG",
                        "At most " + MAX_DISPLAY_NAME + " characters."));
            }
            if (language.direction() == null) {
                errors.add(new ErrorItem(path + ".direction", "REQUIRED", "Choose LTR or RTL."));
            }
        }
        if (requested.stream().filter(LanguageInput::isDefault).count() != 1) {
            errors.add(new ErrorItem("languages", "ONE_DEFAULT_REQUIRED", "Exactly one language must be the default."));
        }
    }

    /**
     * Writes the validated list: the listed languages enabled in order, the others disabled. Refuses a change of
     * the default language while a Resource has no title in the new one.
     */
    public void replace(UUID projectId, List<LanguageInput> requested) {
        LanguageInput newDefault = requested.stream().filter(LanguageInput::isDefault).findFirst().orElseThrow();
        lookup.enabledLanguages(projectId).stream()
                .filter(ProjectLanguage::isDefaultLanguage)
                .findFirst()
                .filter(current -> !current.getLanguageCode().equals(newDefault.languageCode()))
                .ifPresent(current -> requireCompleteDefault(projectId, newDefault.languageCode()));

        List<ProjectLanguage> existing = repository.findByProjectIdOrderBySortOrderAsc(projectId);
        // Clear the old default first, so the one-default index never sees two at once.
        existing.forEach(ProjectLanguage::clearDefault);
        repository.saveAllAndFlush(existing);

        Map<String, ProjectLanguage> byCode = existing.stream()
                .collect(Collectors.toMap(ProjectLanguage::getLanguageCode, Function.identity()));
        Set<String> kept = new HashSet<>();
        List<ProjectLanguage> toSave = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            LanguageInput input = requested.get(i);
            ProjectLanguage language = byCode.getOrDefault(input.languageCode(),
                    ProjectLanguage.of(projectId, input.languageCode()));
            language.enable(ResourceRules.trimToNull(input.displayName()), input.direction(), input.isDefault(), i);
            kept.add(input.languageCode());
            toSave.add(language);
        }
        existing.stream().filter(language -> !kept.contains(language.getLanguageCode())).forEach(language -> {
            language.disable();
            toSave.add(language);
        });
        repository.saveAll(toSave);
    }

    /** Every language row of the Project, enabled or not, by code: initialization reuses their names. */
    public Map<String, ProjectLanguage> allByCode(UUID projectId) {
        return repository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .collect(Collectors.toMap(ProjectLanguage::getLanguageCode, Function.identity()));
    }

    private void requireCompleteDefault(UUID projectId, String newDefault) {
        long total = loader.countResources(projectId);
        long titled = loader.countTitled(projectId, newDefault);
        if (titled < total) {
            throw new ValidationFailedException("The new default language is incomplete.",
                    List.of(new ErrorItem("languages", "DEFAULT_LANGUAGE_INCOMPLETE",
                            (total - titled) + " of " + total + " resources have no title in the new default language.")));
        }
    }
}
