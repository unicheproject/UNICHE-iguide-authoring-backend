package eu.uniche.iguide.template.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.uniche.iguide.template.domain.ApplicationTemplate;
import eu.uniche.iguide.template.domain.ProjectTemplateApplication;
import eu.uniche.iguide.template.domain.StylePreset;
import eu.uniche.iguide.template.infrastructure.ApplicationTemplateRepository;
import eu.uniche.iguide.template.infrastructure.ProjectTemplateApplicationRepository;
import eu.uniche.iguide.template.infrastructure.StylePresetRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Templates, their typed definitions, style presets, and the Template applied to a Project, for the other
 * packages. Project lookups expect the caller to have passed the Project access check.
 */
@Component
public class TemplateLookup {

    /** The Template version a Project was initialized from, with its definition. */
    public record AppliedTemplate(ProjectTemplateApplication application, ApplicationTemplate template,
                                  TemplateDefinition definition) {
    }

    private final ApplicationTemplateRepository templates;
    private final StylePresetRepository presets;
    private final ProjectTemplateApplicationRepository applications;
    private final ObjectMapper objectMapper;

    public TemplateLookup(ApplicationTemplateRepository templates, StylePresetRepository presets,
                          ProjectTemplateApplicationRepository applications, ObjectMapper objectMapper) {
        this.templates = templates;
        this.presets = presets;
        this.applications = applications;
        this.objectMapper = objectMapper;
    }

    public Optional<ApplicationTemplate> find(UUID templateId) {
        return templates.findById(templateId);
    }

    public TemplateDefinition definition(ApplicationTemplate template) {
        return objectMapper.convertValue(template.getDefinition(), TemplateDefinition.class);
    }

    /** The presets with the given keys, in the given order; a key without a row is left out. */
    public Map<String, StylePreset> presets(Collection<String> keys) {
        Map<String, StylePreset> found = new LinkedHashMap<>();
        Map<String, StylePreset> rows = new LinkedHashMap<>();
        presets.findByKeyIn(keys).forEach(row -> rows.put(row.getKey(), row));
        keys.forEach(key -> {
            if (rows.containsKey(key)) {
                found.put(key, rows.get(key));
            }
        });
        return found;
    }

    public Optional<StylePreset> preset(String key) {
        return key == null ? Optional.empty() : presets.findById(key);
    }

    public Optional<AppliedTemplate> applied(UUID projectId) {
        return applications.findByProjectId(projectId).flatMap(application -> templates
                .findById(application.getTemplateId())
                .map(template -> new AppliedTemplate(application, template, definition(template))));
    }
}
