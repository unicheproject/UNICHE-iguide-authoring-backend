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

    // --- Route Stops, Guide Blocks, and Assets (DECISIONS.md D-032) ---------------------------------

    @Test
    void aStopIsTranslatedInItsDraftRouteAndAnUnknownStopIsNotFound() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode route = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes", PROJECT_A),
                Map.of("title", "Διαδρομή", "type", "OUTDOOR"))).andExpect(status().isCreated()).andReturn());
        JsonNode stop = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes/{id}/stops", PROJECT_A,
                        route.get("id").asText()), Map.of("poiId", poi.get("id").asText(),
                        "translations", Map.of("el", Map.of("narrative", "Στάση")))))
                .andExpect(status().isCreated()).andReturn());
        JsonNode current = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/routes/{id}", PROJECT_A,
                route.get("id").asText()))).andReturn());
        String stopUrl = "/api/v1/projects/" + PROJECT_A + "/routes/" + route.get("id").asText() + "/stops/";

        JsonNode saved = read(mockMvc.perform(json(put(stopUrl + stop.get("id").asText() + "/translations/en"),
                        Map.of("version", current.get("version").asLong(), "narrative", "A stop", "titleOverride", "Kouros")))
                .andExpect(status().isOk()).andReturn());
        JsonNode texts = saved.get("stops").get(0).get("translations");
        assertThat(saved.get("stops").get(0).get("id").asText()).isEqualTo(stop.get("id").asText());
        assertThat(texts.get("en").get("narrative").asText()).isEqualTo("A stop");
        assertThat(texts.get("el").get("narrative").asText()).isEqualTo("Στάση");
        assertThat(saved.get("version").asLong()).isEqualTo(current.get("version").asLong() + 1);

        mockMvc.perform(json(put(stopUrl + UUID.randomUUID() + "/translations/en"),
                        Map.of("version", saved.get("version").asLong(), "narrative", "x")))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(delete(stopUrl + stop.get("id").asText() + "/translations/el")
                        .param("version", saved.get("version").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("DEFAULT_LANGUAGE"));
        JsonNode cleared = read(mockMvc.perform(authenticated(delete(stopUrl + stop.get("id").asText() + "/translations/en")
                        .param("version", saved.get("version").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(cleared.get("stops").get(0).get("translations").get("en").get("narrative").isNull()).isTrue();
    }

    @Test
    void aBlockOfAnActiveGuideIsTranslatedThroughItsWorkingCopy() throws Exception {
        JsonNode guide = read(mockMvc.perform(json(post("/api/v1/projects/{p}/guides", PROJECT_A), Map.of("title", "Ιστορία")))
                .andExpect(status().isCreated()).andReturn());
        JsonNode block = read(mockMvc.perform(json(post("/api/v1/projects/{p}/guides/{id}/blocks", PROJECT_A,
                        guide.get("id").asText()), Map.of("blockType", "TEXT", "translations", Map.of("el", Map.of("text",
                        Map.of("blocks", List.of(Map.of("type", "paragraph", "text", "Κείμενο"))))))))
                .andExpect(status().isCreated()).andReturn());
        JsonNode current = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/guides/{id}", PROJECT_A,
                guide.get("id").asText()))).andReturn());
        JsonNode active = changeStatus("/api/v1/projects/" + PROJECT_A + "/guides", guide.get("id").asText(), "ACTIVE",
                current.get("version").asLong(), 200);

        String blockUrl = "/api/v1/projects/" + PROJECT_A + "/guides/" + guide.get("id").asText() + "/blocks/"
                + block.get("id").asText() + "/translations/en";
        mockMvc.perform(json(put(blockUrl), Map.of("version", active.get("version").asLong(),
                        "text", Map.of("blocks", List.of(Map.of("type", "paragraph", "text", "Text"))))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Working-Copy-Version"))
                .andExpect(jsonPath("$.hasWorkingCopy").value(true));
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/guides/{id}", PROJECT_A, guide.get("id").asText())))
                .andExpect(jsonPath("$.blocks[0].translations.en").doesNotExist());

        JsonNode applied = applyWorkingCopy(PROJECT_A, "guides", guide.get("id").asText(), 200);
        assertThat(applied.get("blocks").get(0).get("translations").get("en").get("text").get("blocks").get(0)
                .get("text").asText()).isEqualTo("Text");
    }

    @Test
    void anAssetIsTranslatedOneLanguageAtATime() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(10, 10));
        String url = "/api/v1/projects/" + PROJECT_A + "/assets/" + asset.get("id").asText() + "/translations/";
        JsonNode greek = read(mockMvc.perform(json(put(url + "el"), Map.of("version", asset.get("version").asLong(),
                        "title", "Κούρος", "alternativeText", "Μαρμάρινο άγαλμα")))
                .andExpect(status().isOk()).andReturn());
        JsonNode english = read(mockMvc.perform(json(put(url + "en"), Map.of("version", greek.get("version").asLong(),
                        "title", "Kouros", "alternativeText", "Marble statue")))
                .andExpect(status().isOk()).andReturn());
        assertThat(english.get("translations").get("en").get("alternativeText").asText()).isEqualTo("Marble statue");
        assertThat(english.get("translations").get("el").get("title").asText()).isEqualTo("Κούρος");
        assertThat(english.get("internalName").asText()).isEqualTo(asset.get("internalName").asText());

        mockMvc.perform(json(put(url + "en"), Map.of("version", greek.get("version").asLong(), "title", "Stale")))
                .andExpect(status().isConflict());
        mockMvc.perform(json(put(url + "fr"), Map.of("version", english.get("version").asLong(), "title", "x")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_LANGUAGE"));
        mockMvc.perform(authenticated(delete(url + "el").param("version", english.get("version").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("DEFAULT_LANGUAGE"));
        JsonNode cleared = read(mockMvc.perform(authenticated(delete(url + "en").param("version",
                        english.get("version").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(cleared.get("translations").path("en").path("title").isMissingNode()
                || cleared.get("translations").get("en").get("title").isNull()).isTrue();
        assertThat(cleared.get("translations").get("el").get("title").asText()).isEqualTo("Κούρος");
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
