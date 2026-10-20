package eu.uniche.iguide.language.api;

import eu.uniche.iguide.language.application.GetProjectLanguagesQuery;
import eu.uniche.iguide.language.application.LanguageInput;
import eu.uniche.iguide.language.application.UpdateProjectLanguagesUseCase;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The languages of a Project ({@code 07} section 14, {@code 12} section 7). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/languages")
public class ProjectLanguagesController {

    private final GetProjectLanguagesQuery getLanguages;
    private final UpdateProjectLanguagesUseCase updateLanguages;

    public ProjectLanguagesController(GetProjectLanguagesQuery getLanguages,
                                      UpdateProjectLanguagesUseCase updateLanguages) {
        this.getLanguages = getLanguages;
        this.updateLanguages = updateLanguages;
    }

    @GetMapping
    public ProjectLanguagesResponse get(@PathVariable UUID projectId) {
        return ProjectLanguagesResponse.from(getLanguages.execute(projectId, CurrentPrincipal.require()));
    }

    @PutMapping
    public ProjectLanguagesResponse update(@PathVariable UUID projectId,
                                           @Valid @RequestBody UpdateProjectLanguagesRequest request) {
        return ProjectLanguagesResponse.from(updateLanguages.execute(projectId, CurrentPrincipal.require(),
                request.languages().stream()
                        .map(l -> new LanguageInput(l.languageCode(), l.displayName(), l.direction(), l.isDefault()))
                        .toList()));
    }
}
