package eu.uniche.iguide.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.template.domain.TemplateStatus;
import java.util.Map;

/**
 * One Template version file, {@code src/main/resources/templates/{key}.v{version}.json} ({@code 04} section 15).
 * {@code definitionNode} is the definition as written, kept for storage and the checksum.
 */
public record TemplateFile(String filename,
                           String key,
                           int version,
                           TemplateStatus status,
                           Map<String, String> name,
                           Map<String, String> description,
                           TemplateDefinition definition,
                           JsonNode definitionNode) {
}
