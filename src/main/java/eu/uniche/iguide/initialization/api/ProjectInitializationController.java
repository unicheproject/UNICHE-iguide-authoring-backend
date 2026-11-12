package eu.uniche.iguide.initialization.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import eu.uniche.iguide.initialization.application.InitializeProjectCommand;
import eu.uniche.iguide.initialization.application.InitializeProjectCommand.RequestedLanguage;
import eu.uniche.iguide.initialization.application.InitializeProjectUseCase;
import eu.uniche.iguide.language.domain.TextDirection;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.project.api.ProjectResponse;
import eu.uniche.iguide.surface.domain.Surface;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step 2 of Project creation ({@code 02} section 11, {@code 04} section 8, {@code 12} section 4 Create a Project;
 * DECISIONS.md D-040). Answers the Project as {@code GET /projects/{id}} does.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/initialize")
public class ProjectInitializationController {

    /**
     * @param languages a language is a code ({@code "el"}) or {@code {languageCode, displayName, direction}}
     */
    public record InitializeProjectRequest(UUID templateId, String stylePreset, List<LanguageRequest> languages,
                                           String defaultLanguage, List<Surface> surfaces,
                                           Map<String, Boolean> featureConfiguration) {
    }

    public record LanguageRequest(String languageCode, String displayName, TextDirection direction) {

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static LanguageRequest ofCode(String languageCode) {
            return new LanguageRequest(languageCode, null, null);
        }
    }

    private final InitializeProjectUseCase initialize;

    public ProjectInitializationController(InitializeProjectUseCase initialize) {
        this.initialize = initialize;
    }

    @PostMapping
    public ProjectResponse initialize(@PathVariable UUID projectId, @RequestBody InitializeProjectRequest request) {
        return ProjectResponse.from(initialize.execute(projectId, CurrentPrincipal.require(),
                new InitializeProjectCommand(request.templateId(), request.stylePreset(),
                        request.languages() == null ? null : request.languages().stream()
                                .map(l -> l == null ? null
                                        : new RequestedLanguage(l.languageCode(), l.displayName(), l.direction()))
                                .toList(),
                        request.defaultLanguage(), request.surfaces(), request.featureConfiguration())));
    }
}
