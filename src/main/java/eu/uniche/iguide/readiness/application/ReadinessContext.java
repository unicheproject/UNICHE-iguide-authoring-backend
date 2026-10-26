package eu.uniche.iguide.readiness.application;

import java.util.List;
import java.util.UUID;

/**
 * What every check needs: the Project, its default language, its enabled languages in order, and the statuses and
 * titles of its Resources ({@link ResourceIndex}).
 */
public record ReadinessContext(UUID projectId, String defaultLanguage, List<String> languages, ResourceIndex index) {

    /** The enabled languages other than the default, in order. */
    public List<String> otherLanguages() {
        return languages.stream().filter(code -> !code.equals(defaultLanguage)).toList();
    }
}
