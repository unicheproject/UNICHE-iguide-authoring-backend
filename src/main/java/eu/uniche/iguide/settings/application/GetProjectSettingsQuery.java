package eu.uniche.iguide.settings.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.project.application.ProjectNotInitializedException;
import eu.uniche.iguide.settings.infrastructure.ProjectSettingsRepository;
import eu.uniche.iguide.template.application.TemplateLookup;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** {@code GET .../settings} ({@code 07} section 14); {@code 409 PROJECT_NOT_INITIALIZED} before initialization. */
@Service
public class GetProjectSettingsQuery {

    private final ProjectAccessGateway accessGateway;
    private final ProjectSettingsRepository settings;
    private final TemplateLookup templates;
    private final TransactionTemplate transactionTemplate;

    public GetProjectSettingsQuery(ProjectAccessGateway accessGateway, ProjectSettingsRepository settings,
                                   TemplateLookup templates, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.settings = settings;
        this.templates = templates;
        this.transactionTemplate = transactionTemplate;
    }

    public SettingsView execute(UUID projectId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> load(projectId));
    }

    SettingsView load(UUID projectId) {
        var applied = templates.applied(projectId).orElseThrow(ProjectNotInitializedException::new);
        return new SettingsView(settings.findById(projectId).orElseThrow(ProjectNotInitializedException::new),
                applied.definition().capabilities());
    }
}
