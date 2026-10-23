package eu.uniche.iguide.translation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** One language of a Resource's texts ({@code 07} section 14, {@code 12} section 3; DECISIONS.md D-031). */
class ResourceTranslationIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    @Test
    void aDraftIsTranslatedInPlaceWithItsVersion() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        long version = place.get("version").asLong();

        JsonNode saved = translate(PROJECT_A, "places", place, "en",
                Map.of("version", version, "title", "Acropolis", "summary", "The sacred rock"), 200);
        assertThat(saved.get("translations").get("en").get("title").asText()).isEqualTo("Acropolis");
        assertThat(saved.get("translations").get("el").get("title").asText()).isEqualTo("Ακρόπολη");
        assertThat(saved.get("version").asLong()).isEqualTo(version + 1);
        assertThat(saved.get("hasWorkingCopy").asBoolean()).isFalse();

        JsonNode stale = translate(PROJECT_A, "places", place, "en", Map.of("version", version, "title", "Again"), 409);
        assertThat(stale.get("currentVersion").asLong()).isEqualTo(version + 1);
        JsonNode unknown = translate(PROJECT_A, "places", place, "fr", Map.of("version", version + 1, "title", "x"), 422);
        assertThat(unknown.get("errors").get(0).get("code").asText()).isEqualTo("UNKNOWN_LANGUAGE");

        JsonNode category = read(mockMvc.perform(json(post("/api/v1/projects/{p}/categories", PROJECT_A),
                Map.of("name", "Γλυπτική", "scope", "POI"))).andExpect(status().isCreated()).andReturn());
        JsonNode named = translate(PROJECT_A, "categories", category, "en", Map.of("version", category.get("version").asLong(),
                "name", "Sculpture", "description", "Marble and bronze"), 200);
        assertThat(named.get("translations").get("en").get("name").asText()).isEqualTo("Sculpture");
    }

    @Test
    void anActiveResourceIsTranslatedThroughItsWorkingCopy() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode active = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(), 200);
        long live = active.get("version").asLong();

        MvcResult first = mockMvc.perform(json(put(url(PROJECT_A, "pois", poi, "en")),
                        Map.of("version", live, "title", "Kouros")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Working-Copy-Version"))
                .andExpect(jsonPath("$.hasWorkingCopy").value(true))
                .andExpect(jsonPath("$.translations.en.title").value("Kouros"))
                .andReturn();
        long copyVersion = Long.parseLong(first.getResponse().getHeader("X-Working-Copy-Version"));

        // A second language needs the copy's version, and keeps the first.
        translate(PROJECT_A, "pois", poi, "el", Map.of("version", live, "title", "Νέος Κούρος"), 409);
        JsonNode second = translate(PROJECT_A, "pois", poi, "el",
                Map.of("version", live, "workingCopyVersion", copyVersion, "title", "Νέος Κούρος"), 200);
        assertThat(second.get("translations").get("en").get("title").asText()).isEqualTo("Kouros");

        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", poi.get("id").asText())))
                .andExpect(jsonPath("$.translations.el.title").value("Κούρος"))
                .andExpect(jsonPath("$.translations.en").doesNotExist())
                .andExpect(jsonPath("$.version").value(live));

        JsonNode applied = applyWorkingCopy(PROJECT_A, "pois", poi.get("id").asText(), 200);
        assertThat(applied.get("translations").get("el").get("title").asText()).isEqualTo("Νέος Κούρος");
        assertThat(applied.get("translations").get("en").get("title").asText()).isEqualTo("Kouros");
    }

    @Test
    void childRowsSurviveATranslation() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode route = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes", PROJECT_A),
                Map.of("title", "Διαδρομή", "type", "OUTDOOR"))).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post("/api/v1/projects/{p}/routes/{id}/stops", PROJECT_A, route.get("id").asText()),
                        Map.of("poiId", poi.get("id").asText(), "translations", Map.of("el", Map.of("narrative", "Στάση")))))
                .andExpect(status().isCreated());
        JsonNode withStop = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/routes/{id}", PROJECT_A,
                route.get("id").asText()))).andReturn());

        JsonNode translated = translate(PROJECT_A, "routes", route, "en",
                Map.of("version", withStop.get("version").asLong(), "title", "Walk"), 200);
        assertThat(translated.get("stops")).hasSize(1);
        assertThat(translated.get("stops").get(0).get("id").asText())
                .isEqualTo(withStop.get("stops").get(0).get("id").asText());
        assertThat(translated.get("stops").get(0).get("translations").get("el").get("narrative").asText()).isEqualTo("Στάση");

        JsonNode guide = read(mockMvc.perform(json(post("/api/v1/projects/{p}/guides", PROJECT_A), Map.of("title", "Ιστορία")))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post("/api/v1/projects/{p}/guides/{id}/blocks", PROJECT_A, guide.get("id").asText()),
                        Map.of("blockType", "TEXT", "translations", Map.of("el", Map.of("text", Map.of("blocks",
                                List.of(Map.of("type", "paragraph", "text", "Κείμενο"))))))))
                .andExpect(status().isCreated());
        JsonNode withBlock = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/guides/{id}", PROJECT_A,
                guide.get("id").asText()))).andReturn());
        JsonNode story = translate(PROJECT_A, "guides", guide, "en",
                Map.of("version", withBlock.get("version").asLong(), "title", "Story"), 200);
        assertThat(story.get("blocks")).hasSize(1);
        assertThat(story.get("blocks").get(0).get("id").asText()).isEqualTo(withBlock.get("blocks").get(0).get("id").asText());
    }

    @Test
    void deleteEmptiesALanguageButNeverTheDefault() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        JsonNode saved = translate(PROJECT_A, "places", place, "en",
                Map.of("version", place.get("version").asLong(), "title", "Acropolis"), 200);

        JsonNode cleared = read(mockMvc.perform(authenticated(delete(url(PROJECT_A, "places", place, "en"))
                        .param("version", saved.get("version").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(cleared.get("translations").get("en").get("title").isNull()).isTrue();
        assertThat(cleared.get("translations").get("el").get("title").asText()).isEqualTo("Ακρόπολη");

        mockMvc.perform(authenticated(delete(url(PROJECT_A, "places", place, "el"))
                        .param("version", cleared.get("version").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("DEFAULT_LANGUAGE"));
    }

    @Test
    void anotherProjectsResourceAndKindsWithoutTextsAreNotFound() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        translate(PROJECT_B, "places", place, "en", Map.of("version", place.get("version").asLong(), "title", "x"), 404);
        mockMvc.perform(json(put("/api/v1/projects/{p}/maps/{id}/translations/en", PROJECT_A, UUID.randomUUID()),
                        Map.of("version", 0)))
                .andExpect(status().isNotFound());
    }

    private JsonNode translate(UUID projectId, String kind, JsonNode resource, String language,
                               Map<String, Object> body, int expected) throws Exception {
        return read(mockMvc.perform(json(put(url(projectId, kind, resource, language)), new HashMap<>(body)))
                .andExpect(status().is(expected)).andReturn());
    }

    private static String url(UUID projectId, String kind, JsonNode resource, String language) {
        return "/api/v1/projects/" + projectId + "/" + kind + "/" + resource.get("id").asText() + "/translations/" + language;
    }
}
