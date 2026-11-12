package eu.uniche.iguide.template.application;

import eu.uniche.iguide.template.domain.TemplateStatus;
import eu.uniche.iguide.template.infrastructure.ApplicationTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/templates} ({@code 07} section 14): the {@code ACTIVE} Template versions, the ones that may
 * initialize a Project ({@code 04} section 3). Platform data: a valid token is enough, no Project is involved.
 */
@Service
public class ListTemplatesQuery {

    private final ApplicationTemplateRepository templates;
    private final TemplateLookup lookup;

    public ListTemplatesQuery(ApplicationTemplateRepository templates, TemplateLookup lookup) {
        this.templates = templates;
        this.lookup = lookup;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> execute() {
        return templates.findByStatusOrderByKeyAscVersionDesc(TemplateStatus.ACTIVE).stream()
                .map(template -> {
                    TemplateDefinition definition = lookup.definition(template);
                    return new TemplateView(template, definition,
                            List.copyOf(lookup.presets(definition.stylePresets()).values()));
                })
                .toList();
    }
}
