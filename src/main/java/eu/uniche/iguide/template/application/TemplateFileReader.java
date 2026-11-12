package eu.uniche.iguide.template.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import eu.uniche.iguide.template.domain.TemplateStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Reads the Template and style preset files from the classpath ({@code 04} section 15) strictly: an unknown key, a
 * value outside a closed set, or a missing member fails startup with the file named.
 *
 * <p>The checksum is SHA-256 over the canonical JSON of the checked part (keys sorted, no whitespace), so a line
 * ending or indentation change in a checkout is not a change of the Template (DECISIONS.md D-040).
 */
@Component
public class TemplateFileReader {

    public static final String TEMPLATE_LOCATION = "classpath:templates/*.json";
    public static final String PRESET_LOCATION = "classpath:style-presets/*.json";

    private static final Set<String> TEMPLATE_MEMBERS = Set.of("key", "version", "status", "name", "description",
            "definition");
    private static final Set<String> PRESET_MEMBERS = Set.of("key", "name", "description", "values");
    private static final TypeReference<Map<String, String>> TEXTS = new TypeReference<>() {
    };

    private final ObjectMapper strict;
    private final ObjectMapper canonical;

    public TemplateFileReader(ObjectMapper objectMapper) {
        this.strict = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        this.canonical = objectMapper.copy()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
    }

    public List<TemplateFile> templates(String location) {
        List<TemplateFile> files = new ArrayList<>();
        for (Resource resource : resources(location)) {
            JsonNode root = tree(resource);
            requireMembers(resource, root, TEMPLATE_MEMBERS);
            try {
                files.add(new TemplateFile(resource.getFilename(), root.path("key").asText(null),
                        root.path("version").asInt(0),
                        strict.treeToValue(root.get("status"), TemplateStatus.class),
                        strict.convertValue(root.get("name"), TEXTS),
                        strict.convertValue(root.get("description"), TEXTS),
                        strict.treeToValue(root.get("definition"), TemplateDefinition.class),
                        root.get("definition")));
            } catch (IOException | IllegalArgumentException unreadable) {
                throw new TemplateSeedingException("Template file " + resource.getFilename() + " is invalid: "
                        + unreadable.getMessage(), unreadable);
            }
        }
        return files;
    }

    public List<StylePresetFile> presets(String location) {
        List<StylePresetFile> files = new ArrayList<>();
        for (Resource resource : resources(location)) {
            JsonNode root = tree(resource);
            requireMembers(resource, root, PRESET_MEMBERS);
            try {
                files.add(new StylePresetFile(resource.getFilename(), root.path("key").asText(null),
                        strict.convertValue(root.get("name"), TEXTS),
                        strict.convertValue(root.get("description"), TEXTS),
                        strict.treeToValue(root.get("values"), StyleValues.class)));
            } catch (IOException | IllegalArgumentException unreadable) {
                throw new TemplateSeedingException("Style preset file " + resource.getFilename() + " is invalid: "
                        + unreadable.getMessage(), unreadable);
            }
        }
        return files;
    }

    /** SHA-256 of the canonical JSON of {@code value}, hex encoded. */
    public String checksum(Object value) {
        try {
            Object plain = value instanceof JsonNode node ? canonical.convertValue(node, Object.class) : value;
            byte[] json = canonical.writeValueAsString(plain).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Cannot compute a template checksum", impossible);
        }
    }

    public Map<String, Object> asMap(JsonNode node) {
        return canonical.convertValue(node, new TypeReference<Map<String, Object>>() {
        });
    }

    private List<Resource> resources(String location) {
        try {
            List<Resource> found = new ArrayList<>(List.of(new PathMatchingResourcePatternResolver()
                    .getResources(location)));
            found.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));
            return found;
        } catch (IOException unreadable) {
            throw new TemplateSeedingException("Cannot list " + location, unreadable);
        }
    }

    private JsonNode tree(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = strict.readTree(in);
            if (root == null || !root.isObject()) {
                throw new TemplateSeedingException("File " + resource.getFilename() + " is not a JSON object", null);
            }
            return root;
        } catch (IOException unreadable) {
            throw new TemplateSeedingException("File " + resource.getFilename() + " is not valid JSON: "
                    + unreadable.getMessage(), unreadable);
        }
    }

    private static void requireMembers(Resource resource, JsonNode root, Set<String> members) {
        for (Iterator<String> names = root.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!members.contains(name)) {
                throw new TemplateSeedingException("File " + resource.getFilename() + " has the unknown member '"
                        + name + "'", null);
            }
        }
        for (String member : members) {
            if (root.get(member) == null || root.get(member).isNull()) {
                throw new TemplateSeedingException("File " + resource.getFilename() + " lacks '" + member + "'",
                        null);
            }
        }
    }
}
