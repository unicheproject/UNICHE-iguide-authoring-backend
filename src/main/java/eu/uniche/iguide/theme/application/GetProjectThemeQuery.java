package eu.uniche.iguide.theme.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.project.application.ProjectNotInitializedException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** {@code GET .../theme} ({@code 07} section 14); {@code 409 PROJECT_NOT_INITIALIZED} before initialization. */
@Service
public class GetProjectThemeQuery {

    private final ProjectAccessGateway accessGateway;
    private final EffectiveThemeService themes;
    private final TransactionTemplate transactionTemplate;

    public GetProjectThemeQuery(ProjectAccessGateway accessGateway, EffectiveThemeService themes,
                                TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.themes = themes;
        this.transactionTemplate = transactionTemplate;
    }

    public ThemeView execute(UUID projectId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> themes.resolve(projectId)
                .orElseThrow(ProjectNotInitializedException::new));
    }
}
