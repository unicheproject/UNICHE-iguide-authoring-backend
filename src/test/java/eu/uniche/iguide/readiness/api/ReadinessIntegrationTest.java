package eu.uniche.iguide.readiness.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code GET .../readiness} ({@code 07} section 14, DECISIONS.md D-033): each code from one fixture, gone once
 * fixed; DRAFT Resources ignored; Project isolation.
 */
class ReadinessIntegrationTest extends ContentIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    @Test
    void aProjectWithNothingActiveAndTheWebSurfaceReadyIsActivatable() throws Exception {
        web(PROJECT_A, true, "https://guide.museum.gr");
        createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode readiness = readiness(PROJECT_A);
        assertThat(readiness.get("activatable").asBoolean()).isTrue();
        assertThat(readiness.get("blocking")).isEmpty();
        assertThat(readiness.get("warnings")).isEmpty();
    }

    @Test
    void surfacesBlockOrWarnUntilTheWebSurfaceHasItsAddress() throws Exception {
        JsonNode none = readiness(PROJECT_A);
        assertThat(none.get("activatable").asBoolean()).isFalse();
        assertThat(codes(none.get("blocking"))).containsExactly("PROJECT:surfaces:NO_SURFACE_ENABLED");

        web(PROJECT_A, true, "");
        JsonNode noAddress = readiness(PROJECT_A);
        assertThat(noAddress.get("blocking")).isEmpty();
        assertThat(codes(noAddress.get("warnings"))).containsExactly("PROJECT:surfaces:PUBLIC_BASE_URL_MISSING");

        web(PROJECT_A, true, "https://guide.museum.gr");
        assertThat(readiness(PROJECT_A).get("warnings")).isEmpty();
    }

    @Test
    void anActivePoiReportsItsTextsAndAccessibilityAndADraftNothing() throws Exception {
        web(PROJECT_A, true, "https://guide.museum.gr");
        createPoi(PROJECT_A, "{\"title\": \"Πρόχειρο\"}");
        JsonNode poi = activePoi("Κούρος");

        JsonNode readiness = readiness(PROJECT_A);
        assertThat(readiness.get("activatable").asBoolean()).isTrue();
        assertThat(codes(readiness.get("warnings"))).containsExactlyInAnyOrder(
                "POI:translations.en.title:MISSING_TRANSLATION",
                "POI:translations.el.accessibilityInformation:MISSING_ACCESSIBILITY_INFORMATION",
                "POI:translations.en.accessibilityInformation:MISSING_ACCESSIBILITY_INFORMATION");
        JsonNode first = readiness.get("warnings").get(0);
        assertThat(first.get("resourceId").asText()).isEqualTo(poi.get("id").asText());
        assertThat(first.get("title").asText()).isEqualTo("Κούρος");

        // A default-language title lost outside the API blocks, and its return clears it.
        jdbc.update("UPDATE poi_translations SET title = NULL WHERE poi_id = ?::uuid AND language_code = 'el'",
                poi.get("id").asText());
        JsonNode untitled = readiness(PROJECT_A);
        assertThat(untitled.get("activatable").asBoolean()).isFalse();
        assertThat(codes(untitled.get("blocking"))).containsExactly(
                "POI:translations.el.title:MISSING_DEFAULT_LANGUAGE_VALUE");
        assertThat(untitled.get("blocking").get(0).get("languageCode").asText()).isEqualTo("el");
        jdbc.update("UPDATE poi_translations SET title = 'Κούρος' WHERE poi_id = ?::uuid AND language_code = 'el'",
                poi.get("id").asText());
        assertThat(readiness(PROJECT_A).get("blocking")).isEmpty();

        // Translating the title and filling accessibility clears the warnings.
        jdbc.update("UPDATE poi_translations SET accessibility_information = 'Step-free' WHERE poi_id = ?::uuid",
                poi.get("id").asText());
        jdbc.update("INSERT INTO poi_translations"
                + " (id, project_id, poi_id, language_code, title, accessibility_information)"
                + " VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'en', 'Kouros', 'Step-free')",
                PROJECT_A.toString(), poi.get("id").asText());
        assertThat(readiness(PROJECT_A).get("warnings")).isEmpty();
    }

    @Test
    void aRouteBlocksWithoutAnActiveStopAndDanglingStops() throws Exception {
        web(PROJECT_A, true, "https://guide.museum.gr");
        JsonNode poi = activePoi("Κούρος");
        accessible(poi);
        JsonNode route = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes", PROJECT_A),
                Map.of("title", "Διαδρομή", "type", "OUTDOOR"))).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post("/api/v1/projects/{p}/routes/{id}/stops", PROJECT_A, route.get("id").asText()),
                Map.of("poiId", poi.get("id").asText(), "translations", Map.of()))).andExpect(status().isCreated());
        JsonNode current = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/routes/{id}", PROJECT_A,
                route.get("id").asText()))).andReturn());
        changeStatus("/api/v1/projects/" + PROJECT_A + "/routes", route.get("id").asText(), "ACTIVE",
                current.get("version").asLong(), 200);

        JsonNode oneStop = readiness(PROJECT_A);
        assertThat(oneStop.get("blocking")).isEmpty();
        assertThat(codes(oneStop.get("warnings"))).contains("ROUTE:stops:ROUTE_FEW_STOPS")
                .doesNotContain("ROUTE:stops:ROUTE_WITHOUT_ACTIVE_STOP");

        jdbc.update("UPDATE points_of_interest SET status = 'DRAFT' WHERE id = ?::uuid", poi.get("id").asText());
        assertThat(codes(readiness(PROJECT_A).get("blocking")))
                .containsExactly("ROUTE:stops:ROUTE_WITHOUT_ACTIVE_STOP");

        jdbc.update("UPDATE points_of_interest SET status = 'ARCHIVED' WHERE id = ?::uuid", poi.get("id").asText());
        assertThat(codes(readiness(PROJECT_A).get("blocking"))).containsExactlyInAnyOrder(
                "ROUTE:stops:ROUTE_WITHOUT_ACTIVE_STOP", "ROUTE:stops[0].poiId:DANGLING_REFERENCE");
    }

    @Test
    void aGuideStoryUntranslatedWarnsAndMediaNeedTheirTexts() throws Exception {
        web(PROJECT_A, true, "https://guide.museum.gr");
        JsonNode guide = read(mockMvc.perform(json(post("/api/v1/projects/{p}/guides", PROJECT_A),
                Map.of("title", "Ιστορία"))).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post("/api/v1/projects/{p}/guides/{id}/blocks", PROJECT_A, guide.get("id").asText()),
                        Map.of("blockType", "TEXT", "translations", Map.of("el", Map.of("text", Map.of("blocks",
                                List.of(Map.of("type", "paragraph", "text", "Κείμενο"))))))))
                .andExpect(status().isCreated());
        JsonNode current = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/guides/{id}", PROJECT_A,
                guide.get("id").asText()))).andReturn());
        changeStatus("/api/v1/projects/" + PROJECT_A + "/guides", guide.get("id").asText(), "ACTIVE",
                current.get("version").asLong(), 200);
        assertThat(codes(readiness(PROJECT_A).get("warnings"))).containsExactlyInAnyOrder(
                "GUIDE:translations.en.title:MISSING_TRANSLATION",
                "GUIDE:blocks[0].translations.en.text:MISSING_TRANSLATION");

        JsonNode image = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(10, 10));
        JsonNode texted = read(mockMvc.perform(json(put(assetUrl(PROJECT_A) + "/{id}/translations/el",
                        image.get("id").asText()), Map.of("version", image.get("version").asLong(),
                        "alternativeText", "Μαρμάρινος κούρος")))
                .andExpect(status().isOk()).andReturn());
        changeStatus(assetUrl(PROJECT_A), image.get("id").asText(), "ACTIVE", texted.get("version").asLong(), 200);
        assertThat(readiness(PROJECT_A).get("blocking")).isEmpty();
        jdbc.update("UPDATE asset_translations SET alternative_text = NULL WHERE asset_id = ?::uuid",
                image.get("id").asText());
        assertThat(codes(readiness(PROJECT_A).get("blocking"))).containsExactly(
                "ASSET:translations.el.alternativeText:MISSING_ALTERNATIVE_TEXT");

        JsonNode audio = uploadAsset(PROJECT_A, "story.mp3", "audio/mpeg", new byte[] {1, 2, 3});
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        mockMvc.perform(json(post(poiUrl(PROJECT_A) + "/{id}/resource-assets", poi.get("id").asText()),
                        Map.of("assetId", audio.get("id").asText(), "usage", "AUDIO_NARRATION")))
                .andExpect(status().isCreated());
        assertThat(codes(readiness(PROJECT_A).get("warnings"))).doesNotContain(
                "ASSET:translations.el.transcript:MISSING_TRANSCRIPT");
        changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(), 200);
        assertThat(codes(readiness(PROJECT_A).get("warnings"))).contains(
                "ASSET:translations.el.transcript:MISSING_TRANSCRIPT");
    }

    @Test
    void everyGuideBlockAndCaptionWarnsAndAnImageAloneFillsATextBlock() throws Exception {
        web(PROJECT_A, true, "https://guide.museum.gr");
        JsonNode image = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(10, 10));
        JsonNode texted = read(mockMvc.perform(json(put(assetUrl(PROJECT_A) + "/{id}/translations/el",
                        image.get("id").asText()), Map.of("version", image.get("version").asLong(),
                        "alternativeText", "Μαρμάρινος κούρος")))
                .andExpect(status().isOk()).andReturn());
        changeStatus(assetUrl(PROJECT_A), image.get("id").asText(), "ACTIVE", texted.get("version").asLong(), 200);
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode guide = read(mockMvc.perform(json(post("/api/v1/projects/{p}/guides", PROJECT_A),
                Map.of("title", "Ιστορία"))).andExpect(status().isCreated()).andReturn());
        String blocks = "/api/v1/projects/" + PROJECT_A + "/guides/" + guide.get("id").asText() + "/blocks";
        for (Map<String, Object> block : List.of(
                Map.<String, Object>of("blockType", "TEXT", "translations", Map.of("el", Map.of("text",
                        Map.of("blocks", List.of(Map.of("type", "paragraph", "text", "Κείμενο")))))),
                Map.<String, Object>of("blockType", "TEXT", "translations", Map.of("el", Map.of("text",
                        Map.of("blocks", List.of(Map.of("type", "asset", "assetId", image.get("id").asText())))))),
                Map.<String, Object>of("blockType", "REFERENCE", "resourceType", "POI",
                        "resourceId", poi.get("id").asText(), "translations", Map.of("el", Map.of("caption", "Δείτε"))),
                Map.<String, Object>of("blockType", "REFERENCE", "resourceType", "POI",
                        "resourceId", poi.get("id").asText()))) {
            mockMvc.perform(json(post(blocks), block)).andExpect(status().isCreated());
        }
        JsonNode current = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/guides/{id}", PROJECT_A,
                guide.get("id").asText()))).andReturn());

        // The image-only text block is content, so the Guide activates.
        changeStatus("/api/v1/projects/" + PROJECT_A + "/guides", guide.get("id").asText(), "ACTIVE",
                current.get("version").asLong(), 200);
        assertThat(codes(readiness(PROJECT_A).get("warnings"))).filteredOn(c -> c.startsWith("GUIDE:"))
                .containsExactlyInAnyOrder(
                        "GUIDE:translations.en.title:MISSING_TRANSLATION",
                        "GUIDE:blocks[0].translations.en.text:MISSING_TRANSLATION",
                        "GUIDE:blocks[1].translations.en.text:MISSING_TRANSLATION",
                        "GUIDE:blocks[2].translations.en.caption:MISSING_TRANSLATION");
    }

    @Test
    void aWorkingCopyIsPendingAndEveryProjectIsEvaluatedAlone() throws Exception {
        web(PROJECT_A, true, "https://guide.museum.gr");
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        JsonNode active = changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ACTIVE",
                place.get("version").asLong(), 200);
        saveWorkingCopy(PROJECT_A, "places", place.get("id").asText(), active.get("version").asLong(), null,
                Map.of("slug", "acropolis", "type", "SITE", "translations", Map.of("el", Map.of("title", "Ακρόπολη"))),
                200);
        assertThat(codes(readiness(PROJECT_A).get("warnings"))).contains("PLACE:workingCopy:PENDING_WORKING_COPY");

        JsonNode other = readiness(PROJECT_B);
        assertThat(codes(other.get("blocking"))).containsExactly("PROJECT:surfaces:NO_SURFACE_ENABLED");
        assertThat(other.get("warnings")).isEmpty();
        mockMvc.perform(get("/api/v1/projects/{p}/readiness", PROJECT_A)).andExpect(status().isUnauthorized());
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode readiness(UUID projectId) throws Exception {
        return read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/readiness", projectId)))
                .andExpect(status().isOk()).andReturn());
    }

    private void web(UUID projectId, boolean enabled, String address) throws Exception {
        mockMvc.perform(json(put("/api/v1/projects/{p}/surfaces", projectId), Map.of("surfaces", List.of(Map.of(
                        "surface", "WEB", "enabled", enabled, "configuration", Map.of("publicBaseUrl", address))))))
                .andExpect(status().isOk());
    }

    private JsonNode activePoi(String title) throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"" + title + "\"}");
        return changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(), 200);
    }

    /** Fills accessibility information and the English title, so only the fixture's own code remains. */
    private void accessible(JsonNode poi) {
        jdbc.update("UPDATE poi_translations SET accessibility_information = 'Step-free' WHERE poi_id = ?::uuid",
                poi.get("id").asText());
        jdbc.update("INSERT INTO poi_translations"
                + " (id, project_id, poi_id, language_code, title, accessibility_information)"
                + " VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'en', 'Kouros', 'Step-free')",
                PROJECT_A.toString(), poi.get("id").asText());
    }

    /** Each issue as {@code TYPE:path:CODE}. */
    private static List<String> codes(JsonNode issues) {
        List<String> result = new ArrayList<>();
        issues.forEach(i -> result.add(i.get("resourceType").asText() + ":" + i.get("path").asText() + ":"
                + i.get("code").asText()));
        return result;
    }
}
