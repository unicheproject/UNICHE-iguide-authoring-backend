package eu.uniche.iguide.initialization.application;

import eu.uniche.iguide.category.application.InitialCategoryWriter;
import eu.uniche.iguide.category.application.InitialCategoryWriter.InitialCategory;
import eu.uniche.iguide.initialization.application.InitializeProjectCommand.RequestedLanguage;
import eu.uniche.iguide.language.application.KnownLanguages;
import eu.uniche.iguide.language.application.LanguageInput;
import eu.uniche.iguide.language.application.ProjectLanguagesWriter;
import eu.uniche.iguide.language.domain.ProjectLanguage;
import eu.uniche.iguide.language.domain.TextDirection;
import eu.uniche.iguide.platform.projectaccess.AccessibleProject;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.project.application.ProjectAlreadyInitializedException;
import eu.uniche.iguide.project.application.ProjectInitializationReader;
import eu.uniche.iguide.project.application.ProjectView;
import eu.uniche.iguide.project.domain.Project;
import eu.uniche.iguide.project.infrastructure.ProjectRepository;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.settings.application.FeatureRules;
import eu.uniche.iguide.settings.domain.ProjectSettings;
import eu.uniche.iguide.settings.infrastructure.ProjectSettingsRepository;
import eu.uniche.iguide.surface.application.SurfaceWriter;
import eu.uniche.iguide.surface.domain.Surface;
import eu.uniche.iguide.template.application.StyleValues;
import eu.uniche.iguide.template.application.TemplateDefinition;
import eu.uniche.iguide.template.application.TemplateLookup;
import eu.uniche.iguide.template.domain.ApplicationTemplate;
import eu.uniche.iguide.template.domain.ProjectTemplateApplication;
import eu.uniche.iguide.template.domain.StylePreset;
import eu.uniche.iguide.template.domain.TemplateStatus;
import eu.uniche.iguide.template.infrastructure.ProjectTemplateApplicationRepository;
import eu.uniche.iguide.theme.application.ThemeWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code POST /api/v1/projects/{projectId}/initialize} ({@code 04} section 8, {@code 07} section 8, {@code 02}
 * section 11 step 2): materializes a copy of an {@code ACTIVE} Template version into the Project in one database
 * transaction. Idempotent while {@code initializedAt} is null — a failed attempt rolled back completely, and rows
 * the author set before (languages, surfaces, Categories with a Template code) are updated, never duplicated — and
 * {@code 409 PROJECT_ALREADY_INITIALIZED} afterwards.
 *
 * <p>Steps: access check; lock the companion row and check {@code initializedAt}; load the Template; settings;
 * theme from the preset; languages; surfaces; Categories; the provenance row; {@code initializedAt}. Not yet: Pages,
 * Page Components, and Navigation (Milestone 6) and the {@code TEMPLATE_APPLIED} Audit Event (Milestone 7), GAPS.md
 * G-038.
 */
@Service
public class InitializeProjectUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectRepository projects;
    private final TemplateLookup templates;
    private final ProjectTemplateApplicationRepository applications;
    private final ProjectSettingsRepository settings;
    private final ProjectLanguagesWriter languages;
    private final SurfaceWriter surfaces;
    private final ThemeWriter themes;
    private final InitialCategoryWriter categories;
    private final ProjectInitializationReader reader;
    private final TransactionTemplate transactionTemplate;

    public InitializeProjectUseCase(ProjectAccessGateway accessGateway, ProjectRepository projects,
                                    TemplateLookup templates, ProjectTemplateApplicationRepository applications,
                                    ProjectSettingsRepository settings, ProjectLanguagesWriter languages,
                                    SurfaceWriter surfaces, ThemeWriter themes, InitialCategoryWriter categories,
                                    ProjectInitializationReader reader, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.projects = projects;
        this.templates = templates;
        this.applications = applications;
        this.settings = settings;
        this.languages = languages;
        this.surfaces = surfaces;
        this.themes = themes;
        this.categories = categories;
        this.reader = reader;
        this.transactionTemplate = transactionTemplate;
    }

    public ProjectView execute(UUID projectId, UnichePrincipal principal, InitializeProjectCommand command) {
        AccessibleProject accessible = accessGateway.requireAccessibleProject(projectId, principal);
        Project project = transactionTemplate.execute(status -> initialize(projectId, principal, command));
        return ProjectView.of(accessible, project.getLocalDescription(), true)
                .withInitialization(reader.read(projectId));
    }

    private Project initialize(UUID projectId, UnichePrincipal principal, InitializeProjectCommand command) {
        Project project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("The Project does not exist."));
        if (project.getInitializedAt() != null) {
            throw new ProjectAlreadyInitializedException();
        }

        ApplicationTemplate template = requireTemplate(command.templateId());
        TemplateDefinition definition = templates.definition(template);
        List<ErrorItem> errors = new ArrayList<>();

        String presetKey = command.stylePreset() == null ? definition.defaultStylePreset()
                : ResourceRules.trimToNull(command.stylePreset());
        StylePreset preset = null;
        if (presetKey == null || !definition.stylePresets().contains(presetKey)) {
            errors.add(new ErrorItem("stylePreset", "STYLE_PRESET_NOT_OFFERED",
                    "Choose one of the Template's styles."));
        } else {
            preset = templates.preset(presetKey).orElseThrow();
        }

        List<LanguageInput> languageInputs = languages(projectId, command, definition, errors);
        Set<Surface> enabledSurfaces = surfaces(command.surfaces(), definition, errors);
        Map<String, Boolean> features = FeatureRules.apply(definition.capabilities(),
                FeatureRules.defaults(definition.capabilities()), command.featureConfiguration(), errors);
        ResourceRules.throwIfAny(errors, "The initialization request is invalid.");

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ProjectSettings projectSettings = settings.findById(projectId)
                .orElseGet(() -> ProjectSettings.initial(projectId, now));
        projectSettings.configureFeatures(features, now);
        settings.save(projectSettings);

        themes.initialize(projectId, presetKey, StyleValues.fromMap(preset.getValues()), definition.defaultShell(),
                now);
        languages.replace(projectId, languageInputs);
        surfaces.initialize(projectId, enabledSurfaces, definition.runtimeDefaultsOrEmpty(), now);
        categories.materialize(projectId, definition.initialCategoriesOrEmpty().stream()
                        .map(c -> new InitialCategory(c.code(), c.scope(), c.icon(), c.name(), c.description()))
                        .toList(),
                languageInputs.stream().map(LanguageInput::languageCode).collect(Collectors.toSet()),
                principal.subject(), now);
        // Steps 9 to 11, Pages, Page Components, and Navigation, wait for Milestone 6: seeded Templates define none.

        ProjectTemplateApplication application = applications.findByProjectId(projectId)
                .orElseGet(() -> ProjectTemplateApplication.of(projectId));
        application.apply(template, snapshot(template), principal.subject(), now);
        applications.save(application);

        project.markInitialized(now);
        return projects.saveAndFlush(project);
    }

    private ApplicationTemplate requireTemplate(UUID templateId) {
        if (templateId == null) {
            throw invalid("templateId", "REQUIRED", "Choose a Template.");
        }
        ApplicationTemplate template = templates.find(templateId).orElseThrow(() -> invalid(
                "templateId", "TEMPLATE_NOT_FOUND", "No such Template."));
        if (template.getStatus() != TemplateStatus.ACTIVE) {
            throw invalid("templateId", "TEMPLATE_NOT_ACTIVE",
                    "Only an ACTIVE Template version initializes a Project.");
        }
        return template;
    }

    /**
     * The requested languages, or the Template's {@code defaultLanguages}; the name and direction come from the
     * request, else the Project's existing row, else the known names.
     */
    private List<LanguageInput> languages(UUID projectId, InitializeProjectCommand command,
                                          TemplateDefinition definition, List<ErrorItem> errors) {
        List<RequestedLanguage> requested = command.languages() != null && !command.languages().isEmpty()
                ? command.languages()
                : definition.defaultLanguagesOrEmpty().stream()
                        .map(code -> new RequestedLanguage(code, null, null)).toList();
        Map<String, ProjectLanguage> existing = languages.allByCode(projectId);
        String defaultLanguage = ResourceRules.trimToNull(command.defaultLanguage());
        if (defaultLanguage != null && requested.stream().noneMatch(l -> defaultLanguage.equals(l.languageCode()))) {
            errors.add(new ErrorItem("defaultLanguage", "DEFAULT_LANGUAGE_NOT_LISTED",
                    "The default language must be one of the languages."));
        }
        String chosenDefault = defaultLanguage != null
                && requested.stream().anyMatch(l -> defaultLanguage.equals(l.languageCode()))
                ? defaultLanguage
                : requested.isEmpty() ? null : requested.get(0).languageCode();
        List<LanguageInput> inputs = new ArrayList<>();
        boolean defaultTaken = false;
        for (RequestedLanguage language : requested) {
            String code = language == null ? null : ResourceRules.trimToNull(language.languageCode());
            ProjectLanguage row = code == null ? null : existing.get(code);
            KnownLanguages.Known known = KnownLanguages.find(code).orElse(null);
            String displayName = language != null && ResourceRules.trimToNull(language.displayName()) != null
                    ? language.displayName()
                    : row != null ? row.getDisplayName() : known != null ? known.displayName() : null;
            TextDirection direction = language != null && language.direction() != null ? language.direction()
                    : row != null ? row.getDirection() : known != null ? known.direction() : null;
            boolean isDefault = !defaultTaken && code != null && code.equals(chosenDefault);
            defaultTaken |= isDefault;
            inputs.add(new LanguageInput(code, displayName, direction, isDefault));
        }
        ProjectLanguagesWriter.validate(inputs, errors);
        return inputs;
    }

    /** The requested surfaces, or every supported one; each must be supported ({@code 04} section 8 step 7). */
    private static Set<Surface> surfaces(List<Surface> requested, TemplateDefinition definition,
                                         List<ErrorItem> errors) {
        if (requested == null) {
            return EnumSet.copyOf(definition.supportedSurfaces());
        }
        if (requested.isEmpty()) {
            errors.add(new ErrorItem("surfaces", "REQUIRED", "Enable at least one surface."));
            return EnumSet.noneOf(Surface.class);
        }
        Set<Surface> result = EnumSet.noneOf(Surface.class);
        for (int i = 0; i < requested.size(); i++) {
            Surface surface = requested.get(i);
            String path = "surfaces[" + i + "]";
            if (surface == null) {
                errors.add(new ErrorItem(path, "REQUIRED", "Choose WEB, MOBILE, or KIOSK."));
            } else if (!definition.supportedSurfaces().contains(surface)) {
                errors.add(new ErrorItem(path, "SURFACE_NOT_SUPPORTED", "The Template does not support this surface."));
            } else if (!result.add(surface)) {
                errors.add(new ErrorItem(path, "DUPLICATE_VALUE", "The surface is listed twice."));
            }
        }
        return result;
    }

    private static ValidationFailedException invalid(String path, String code, String message) {
        return new ValidationFailedException("The initialization request is invalid.",
                List.of(new ErrorItem(path, code, message)));
    }

    /** The Template version as applied ({@code 04} section 7 {@code originalTemplateSnapshot}). */
    private static Map<String, Object> snapshot(ApplicationTemplate template) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("key", template.getKey());
        snapshot.put("version", template.getVersion());
        snapshot.put("name", template.getName());
        snapshot.put("description", template.getDescription());
        snapshot.put("checksum", template.getChecksum());
        snapshot.put("definition", template.getDefinition());
        return snapshot;
    }
}
