package eu.uniche.iguide.theme.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.uniche.iguide.support.InitializationFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code GET}, {@code PUT .../theme} ({@code 04} sections 9 to 11; DECISIONS.md D-040): typed values within the
 * Template's allowed values, WCAG contrast, the preset and the Custom flag, the shell schema, same-Project logos, and
 * Template data never written.
 */
class ProjectThemeIntegrationTest extends InitializationFixtures {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void noThemeBeforeInitialization() throws Exception {
        mockMvc.perform(authenticated(get(url(PROJECT_A))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_INITIALIZED"));
        mockMvc.perform(json(put(url(PROJECT_A)), "{\"version\": 0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_INITIALIZED"));
    }

    @Test
    void aChangedValueMakesTheStyleCustomAndLeavesTheTemplateAlone() throws Exception {
        initializeMuseum(PROJECT_A);
        String templateBefore = templateData();
        ObjectNode body = body(PROJECT_A);
        body.put("primaryColor", "#14795a");
        body.putObject("shell").put("search", "OFF").put("brandWordmark", " Cycladic ");
        JsonNode saved = read(mockMvc.perform(json(put(url(PROJECT_A)), body)).andExpect(status().isOk()).andReturn());
        assertThat(saved.get("primaryColor").asText()).isEqualTo("#14795A");
        assertThat(saved.get("stylePresetKey").asText()).isEqualTo("gallery-house");
        assertThat(saved.get("stylePresetModified").asBoolean()).isTrue();
        assertThat(saved.at("/shell/search").asText()).isEqualTo("OFF");
        assertThat(saved.at("/shell/brandWordmark").asText()).isEqualTo("Cycladic");
        assertThat(saved.at("/shell/kioskHome").asText()).isEqualTo("TILES");
        assertThat(saved.get("version").asLong()).isEqualTo(1);
        assertThat(templateData()).isEqualTo(templateBefore);

        // Choosing another preset is a PUT with its values: the style is that preset again, not Custom.
        ObjectNode sixNights = body(PROJECT_A);
        sixNights.put("stylePresetKey", "six-nights").put("headingFont", "SYNE").put("bodyFont", "ARCHIVO")
                .put("primaryColor", "#FF3B5C").put("secondaryColor", "#FFD23F").put("backgroundColor", "#111114")
                .put("textColor", "#F4F4F6").put("cornerRadius", "ROUND").put("density", "REGULAR");
        mockMvc.perform(json(put(url(PROJECT_A)), sixNights))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stylePresetKey").value("six-nights"))
                .andExpect(jsonPath("$.stylePresetModified").value(false))
                .andExpect(jsonPath("$.shell.search").value("OFF"));

        // A null typed value takes the Template default (04 section 10).
        ObjectNode unset = body(PROJECT_A);
        unset.putNull("stylePresetKey");
        unset.putNull("headingFont");
        mockMvc.perform(json(put(url(PROJECT_A)), unset))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headingFont").isEmpty())
                .andExpect(jsonPath("$.effective.headingFont").value("SPECTRAL"));
    }

    @Test
    void valuesOutsideTheTemplateAndPoorContrastAreRejected() throws Exception {
        initializeMuseum(PROJECT_A);
        ObjectNode body = body(PROJECT_A);
        body.put("headingFont", "COMIC_SANS").put("density", "TIGHT").put("primaryColor", "purple")
                .put("stylePresetKey", "no-such-style");
        body.putObject("shell").put("search", "FOOTER").put("weather", "ON");
        JsonNode problem = read(mockMvc.perform(json(put(url(PROJECT_A)), body))
                .andExpect(status().isUnprocessableEntity()).andReturn());
        assertThat(codes(problem)).containsExactlyInAnyOrder("headingFont:VALUE_NOT_ALLOWED",
                "density:VALUE_NOT_ALLOWED", "primaryColor:INVALID_COLOR", "stylePresetKey:STYLE_PRESET_NOT_OFFERED",
                "shell.search:VALUE_NOT_ALLOWED", "shell.weather:UNKNOWN_KEY");
        assertThat(problem.toString()).doesNotContain("COMIC_SANS").doesNotContain("purple");

        ObjectNode contrast = body(PROJECT_A);
        contrast.put("textColor", "#777777").put("secondaryColor", "#EEEEEE");
        JsonNode low = read(mockMvc.perform(json(put(url(PROJECT_A)), contrast))
                .andExpect(status().isUnprocessableEntity()).andReturn());
        assertThat(codes(low)).containsExactlyInAnyOrder("textColor:CONTRAST_TOO_LOW",
                "secondaryColor:CONTRAST_TOO_LOW");

        mockMvc.perform(authenticated(get(url(PROJECT_A))))
                .andExpect(jsonPath("$.textColor").value("#030712"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void aStaleVersionConflicts() throws Exception {
        initializeMuseum(PROJECT_A);
        ObjectNode body = body(PROJECT_A);
        body.put("version", 7);
        mockMvc.perform(json(put(url(PROJECT_A)), body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void logosAreImagesOfTheSameProject() throws Exception {
        initializeMuseum(PROJECT_A);
        initializeMuseum(PROJECT_B);
        JsonNode own = uploadAsset(PROJECT_A, "logo.png", "image/png", png(40, 20));
        JsonNode foreign = uploadAsset(PROJECT_B, "logo.png", "image/png", png(40, 20));
        JsonNode pdf = uploadAsset(PROJECT_A, "a.pdf", "application/pdf", "%PDF-1.4".getBytes());

        ObjectNode body = body(PROJECT_A);
        body.put("logoAssetId", foreign.get("id").asText()).put("faviconAssetId", pdf.get("id").asText());
        JsonNode problem = read(mockMvc.perform(json(put(url(PROJECT_A)), body))
                .andExpect(status().isUnprocessableEntity()).andReturn());
        assertThat(codes(problem)).containsExactlyInAnyOrder("logoAssetId:ASSET_NOT_FOUND",
                "faviconAssetId:ASSET_NOT_IMAGE");

        body = body(PROJECT_A);
        body.put("logoAssetId", own.get("id").asText());
        mockMvc.perform(json(put(url(PROJECT_A)), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoAssetId").value(own.get("id").asText()));
        mockMvc.perform(authenticated(get(url(PROJECT_B))))
                .andExpect(jsonPath("$.logoAssetId").isEmpty());
    }

    /** The current theme as a complete PUT body. */
    private ObjectNode body(UUID projectId) throws Exception {
        JsonNode current = read(mockMvc.perform(authenticated(get(url(projectId)))).andExpect(status().isOk())
                .andReturn());
        ObjectNode body = objectMapper.createObjectNode();
        for (String field : List.of("stylePresetKey", "headingFont", "bodyFont", "primaryColor", "secondaryColor",
                "backgroundColor", "textColor", "cornerRadius", "density", "logoAssetId", "compactLogoAssetId",
                "faviconAssetId", "splashAssetId", "shell", "version")) {
            body.set(field, current.get(field));
        }
        return body;
    }

    private String templateData() {
        return jdbc.queryForObject("SELECT checksum || definition::text FROM application_templates WHERE id = ?",
                String.class, MUSEUM) + jdbc.queryForObject(
                "SELECT checksum || preset_values::text FROM style_presets WHERE preset_key = 'gallery-house'",
                String.class);
    }

    private static List<String> codes(JsonNode problem) {
        List<String> codes = new ArrayList<>();
        problem.get("errors").forEach(e -> codes.add(e.get("path").asText() + ":" + e.get("code").asText()));
        return codes;
    }

    private static String url(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/theme";
    }
}
