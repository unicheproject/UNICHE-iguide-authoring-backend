package eu.uniche.iguide.template.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.InitializationFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/templates} and {@code /templates/{id}} ({@code 07} section 14, {@code 04} sections 4, 6, 9,
 * 15; {@code 12} section 4 Create a Project): read-only, localized, with what the CMS draws.
 */
class TemplateIntegrationTest extends InitializationFixtures {

    @Test
    void listsTheActiveTemplatesWithWhatTheCmsDraws() throws Exception {
        JsonNode list = read(mockMvc.perform(authenticated(get("/api/v1/templates")))
                .andExpect(status().isOk()).andReturn()).get("templates");
        List<String> keys = new ArrayList<>();
        list.forEach(t -> keys.add(t.get("key").asText() + ".v" + t.get("version").asInt()));
        assertThat(keys).containsExactly("multi-region-network.v1", "museum-multi-surface.v1", "outdoor-trail.v1");

        JsonNode museum = list.get(1);
        assertThat(museum.get("id").asText()).isEqualTo(MUSEUM.toString());
        assertThat(museum.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(museum.get("name").asText()).isEqualTo("Museum multi-surface");
        assertThat(museum.get("supportedSurfaces").toString()).isEqualTo("[\"WEB\",\"KIOSK\"]");
        assertThat(museum.get("capabilities").size()).isEqualTo(11);
        assertThat(museum.at("/defaultLanguages/0/languageCode").asText()).isEqualTo("el");
        assertThat(museum.at("/defaultLanguages/0/displayName").asText()).isEqualTo("Ελληνικά");
        assertThat(museum.at("/defaultLanguages/0/isDefault").asBoolean()).isTrue();
        assertThat(museum.get("defaultStylePreset").asText()).isEqualTo("gallery-house");
        assertThat(museum.get("stylePresets").size()).isEqualTo(9);
        JsonNode gallery = museum.get("stylePresets").get(0);
        assertThat(gallery.get("key").asText()).isEqualTo("gallery-house");
        assertThat(gallery.get("name").asText()).isEqualTo("Gallery House");
        assertThat(gallery.get("suggested").asBoolean()).isTrue();
        assertThat(gallery.get("headingFont").asText()).isEqualTo("SPECTRAL");
        assertThat(gallery.get("primaryColor").asText()).isEqualTo("#4C1D8B");
        assertThat(gallery.get("backgroundColor").asText()).isEqualTo("#FFFFFF");
        assertThat(gallery.get("textColor").asText()).isEqualTo("#030712");
        assertThat(gallery.get("cornerRadius").asText()).isEqualTo("SOFT");
        assertThat(museum.get("stylePresets").get(1).get("suggested").asBoolean()).isFalse();
        assertThat(museum.get("fonts").findValuesAsText("family")).contains("Spectral", "Archivo", "Baloo 2");
        assertThat(museum.at("/themeValues/cornerRadius").size()).isEqualTo(3);
        assertThat(museum.at("/shellSchema/kioskHome").toString()).isEqualTo("[\"TILES\",\"HEADER_MENU\"]");
        assertThat(museum.at("/defaultShell/languageSwitcher").asText()).isEqualTo("HEADER");
        assertThat(museum.at("/initialCategories/0/name").asText()).isEqualTo("Highlights");
    }

    @Test
    void namesFollowTheCallersLanguageAndFallBackToEnglish() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/templates/{id}", OUTDOOR)).header("Accept-Language", "el-GR,en;q=0.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Υπαίθρια διαδρομή"))
                .andExpect(jsonPath("$.stylePresets[1].name").value("Μονοπάτι λατομείου"))
                .andExpect(jsonPath("$.stylePresets[1].suggested").value(true));
        mockMvc.perform(authenticated(get("/api/v1/templates/{id}", OUTDOOR)).header("Accept-Language", "fr"))
                .andExpect(jsonPath("$.name").value("Outdoor trail"));
    }

    @Test
    void anUnknownTemplateIsNotFoundAndTemplatesAreReadOnly() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/templates/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(json(post("/api/v1/templates"), "{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/v1/templates"))
                .andExpect(status().isUnauthorized());
    }
}
