package eu.uniche.iguide.template.application;

import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/templates/{templateId}} ({@code 07} section 14): one Template version in any status, so the
 * CMS can read the definition of a deprecated version a Project still uses.
 */
@Service
public class GetTemplateQuery {

    private final TemplateLookup lookup;

    public GetTemplateQuery(TemplateLookup lookup) {
        this.lookup = lookup;
    }

    @Transactional(readOnly = true)
    public TemplateView execute(UUID templateId) {
        return lookup.find(templateId)
                .map(template -> {
                    TemplateDefinition definition = lookup.definition(template);
                    return new TemplateView(template, definition,
                            List.copyOf(lookup.presets(definition.stylePresets()).values()));
                })
                .orElseThrow(() -> new ResourceNotFoundException("The Template does not exist."));
    }
}
