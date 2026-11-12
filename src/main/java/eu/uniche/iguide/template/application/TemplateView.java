package eu.uniche.iguide.template.application;

import eu.uniche.iguide.template.domain.ApplicationTemplate;
import eu.uniche.iguide.template.domain.StylePreset;
import java.util.List;

/** One Template version with its typed definition and the style presets it offers, in its order. */
public record TemplateView(ApplicationTemplate template, TemplateDefinition definition, List<StylePreset> presets) {
}
