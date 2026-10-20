package eu.uniche.iguide.language.application;

import eu.uniche.iguide.language.domain.ProjectLanguage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds {@link ProjectLanguagesView}; the counts come from every {@link TitledResourceCounter}. */
@Component
public class ProjectLanguagesLoader {

    private final ProjectLanguageLookup lookup;
    private final List<TitledResourceCounter> counters;

    public ProjectLanguagesLoader(ProjectLanguageLookup lookup, List<TitledResourceCounter> counters) {
        this.lookup = lookup;
        this.counters = counters;
    }

    public ProjectLanguagesView load(UUID projectId) {
        List<ProjectLanguage> languages = lookup.enabledLanguages(projectId);
        Map<String, Long> titled = new LinkedHashMap<>();
        languages.forEach(language -> titled.put(language.getLanguageCode(),
                countTitled(projectId, language.getLanguageCode())));
        return new ProjectLanguagesView(languages, countResources(projectId), titled);
    }

    public long countResources(UUID projectId) {
        return counters.stream().mapToLong(counter -> counter.countResources(projectId)).sum();
    }

    public long countTitled(UUID projectId, String languageCode) {
        return counters.stream().mapToLong(counter -> counter.countTitled(projectId, languageCode)).sum();
    }
}
