package eu.uniche.iguide.settings.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.settings.domain.ProjectSettings;
import eu.uniche.iguide.settings.infrastructure.ProjectSettingsRepository;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PUT .../settings} ({@code 03} section 2, {@code 12} section 3 Features tab): sets the listed capabilities
 * and the timezone. A capability the applied Template does not declare is refused; an unlisted one keeps its value.
 * {@code applicationStatus} changes only through {@code settings/application-status} (not built yet, GAPS.md G-038).
 */
@Service
public class UpdateProjectSettingsUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectSettingsRepository repository;
    private final GetProjectSettingsQuery query;
    private final TransactionTemplate transactionTemplate;
    private final ContentRevision contentRevision;

    public UpdateProjectSettingsUseCase(ProjectAccessGateway accessGateway, ProjectSettingsRepository repository,
                                        GetProjectSettingsQuery query, TransactionTemplate transactionTemplate,
                                        ContentRevision contentRevision) {
        this.accessGateway = accessGateway;
        this.repository = repository;
        this.query = query;
        this.transactionTemplate = transactionTemplate;
        this.contentRevision = contentRevision;
    }

    public SettingsView execute(UUID projectId, UnichePrincipal principal, Map<String, Boolean> featureConfiguration,
                                String timezone, Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            // First, so the settings read below carry the new revision; a refused save rolls it back.
            contentRevision.increment(projectId);
            SettingsView current = query.load(projectId);
            ProjectSettings settings = current.settings();
            ResourceRules.requireCurrentVersion(settings.getVersion(), version, "settings");
            List<ErrorItem> errors = new ArrayList<>();
            Map<String, Boolean> features = FeatureRules.apply(current.capabilities(),
                    settings.getFeatureConfiguration(), featureConfiguration, errors);
            String zone = ResourceRules.trimToNull(timezone);
            if (zone != null) {
                try {
                    zone = ZoneId.of(zone).getId();
                } catch (DateTimeException unknown) {
                    errors.add(new ErrorItem("timezone", "INVALID_TIMEZONE",
                            "Use an IANA time zone such as Europe/Athens."));
                }
            }
            ResourceRules.throwIfAny(errors, "The settings are invalid.");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            settings.configureFeatures(features, now);
            settings.changeTimezone(zone, now);
            repository.saveAndFlush(settings);
            return new SettingsView(settings, current.capabilities());
        });
    }
}
