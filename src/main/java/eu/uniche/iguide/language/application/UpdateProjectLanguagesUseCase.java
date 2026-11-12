package eu.uniche.iguide.language.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.settings.application.ContentRevision;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Replaces the enabled languages of a Project with the requested list ({@code 03} section 3, {@code 12}
 * section 4); the rules are those of {@link ProjectLanguagesWriter}. Works before and after initialization.
 *
 * <p>{@code isDefault} is the only record of the default language (DECISIONS.md D-019, D-040).
 */
@Service
public class UpdateProjectLanguagesUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguagesWriter writer;
    private final ProjectLanguagesLoader loader;
    private final TransactionTemplate transactionTemplate;
    private final ContentRevision contentRevision;

    public UpdateProjectLanguagesUseCase(ProjectAccessGateway accessGateway, ProjectLanguagesWriter writer,
                                         ProjectLanguagesLoader loader, TransactionTemplate transactionTemplate,
                                         ContentRevision contentRevision) {
        this.accessGateway = accessGateway;
        this.writer = writer;
        this.loader = loader;
        this.transactionTemplate = transactionTemplate;
        this.contentRevision = contentRevision;
    }

    public ProjectLanguagesView execute(UUID projectId, UnichePrincipal principal, List<LanguageInput> requested) {
        accessGateway.requireAccess(projectId, principal);
        List<ErrorItem> errors = new ArrayList<>();
        ProjectLanguagesWriter.validate(requested, errors);
        ResourceRules.throwIfAny(errors, "The languages are invalid.");
        transactionTemplate.executeWithoutResult(status -> {
            writer.replace(projectId, requested);
            contentRevision.increment(projectId);
        });
        return loader.load(projectId);
    }
}
