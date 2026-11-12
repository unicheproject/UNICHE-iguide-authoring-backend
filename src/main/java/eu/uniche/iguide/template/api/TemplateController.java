package eu.uniche.iguide.template.api;

import eu.uniche.iguide.template.api.TemplateResponse.TemplatesResponse;
import eu.uniche.iguide.template.application.GetTemplateQuery;
import eu.uniche.iguide.template.application.ListTemplatesQuery;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Templates are read-only for API clients ({@code 07} section 14, {@code 04} section 15): no write endpoint
 * exists. Platform data, not Project-scoped, so a valid token is enough.
 */
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final ListTemplatesQuery listTemplates;
    private final GetTemplateQuery getTemplate;

    public TemplateController(ListTemplatesQuery listTemplates, GetTemplateQuery getTemplate) {
        this.listTemplates = listTemplates;
        this.getTemplate = getTemplate;
    }

    @GetMapping
    public TemplatesResponse list() {
        return new TemplatesResponse(listTemplates.execute().stream().map(TemplateResponse::from).toList());
    }

    @GetMapping("/{templateId}")
    public TemplateResponse get(@PathVariable UUID templateId) {
        return TemplateResponse.from(getTemplate.execute(templateId));
    }
}
