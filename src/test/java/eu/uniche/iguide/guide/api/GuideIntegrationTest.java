package eu.uniche.iguide.guide.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Guides and their blocks end to end against PostgreSQL ({@code 03} section 11, {@code 08} Milestone 4). */
class GuideIntegrationTest extends ContentIntegrationTest {

    private static final Map<String, Object> STORY = Map.of("blocks", List.of(
            Map.of("type", "heading", "level", 2, "text", " Cycladic art "),
            Map.of("type", "paragraph", "text", "Marble figures from the islands."),
            Map.of("type", "paragraph", "text", "   ")));

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    // --- Create and update -----------------------------------------------------------------------

    @Test
    void createsADraftWithItsTitleAndAGeneratedSlugAndSavesItsFields() throws Exception {
        JsonNode guide = createGuide(PROJECT_A, "Cycladic art for children");
        assertThat(guide.get("status").asText()).isEqualTo("DRAFT");
        assertThat(guide.get("slug").asText()).isEqualTo("cycladic-art-for-children");
        assertThat(guide.get("blocks")).isEmpty();

        JsonNode saved = update(guide, Map.of("estimatedReadingMinutes", 7, "featured", true,
                "translations", Map.of("en", Map.of("title", "Cycladic art", "summary", "For young visitors"))), 200);
        assertThat(saved.get("estimatedReadingMinutes").asInt()).isEqualTo(7);
        assertThat(saved.get("featured").asBoolean()).isTrue();
        assertThat(saved.get("translations").get("en").get("summary").asText()).isEqualTo("For young visitors");

        assertThat(update(saved, Map.of("estimatedReadingMinutes", 601), 422).get("errors").get(0).get("path").asText())
                .isEqualTo("estimatedReadingMinutes");
        assertThat(update(guide, Map.of(), 409).get("type").asText()).isEqualTo("VERSION_CONFLICT");
    }

    // --- Blocks ----------------------------------------------------------------------------------

    @Test
    void textAndReferenceBlocksKeepTheirOrderAndTexts() throws Exception {
        JsonNode guide = createGuide(PROJECT_A, "Highlights");
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        JsonNode text = addBlock(guide, Map.of("blockType", "TEXT", "translations", Map.of("el", Map.of("text", STORY))), 201);
        JsonNode reference = addBlock(guide, Map.of("blockType", "REFERENCE", "resourceType", "POI",
                "resourceId", poi.get("id").asText(), "translations", Map.of("en", Map.of("caption", " Look up "))), 201);

        // Text is trimmed and the empty paragraph dropped.
        JsonNode nodes = text.get("translations").get("el").get("text").get("blocks");
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).get("text").asText()).isEqualTo("Cycladic art");
        assertThat(reference.get("translations").get("en").get("caption").asText()).isEqualTo("Look up");
        assertThat(List.of(text.get("sortOrder").asInt(), reference.get("sortOrder").asInt())).containsExactly(0, 1);

        mockMvc.perform(json(put(blocksUrl(guide) + ":order"),
                        Map.of("ids", List.of(reference.get("id").asText(), text.get("id").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].blockType").value("REFERENCE"));

        mockMvc.perform(authenticated(delete(blocksUrl(guide) + "/{b}", reference.get("id").asText())))
                .andExpect(status().isNoContent());
        JsonNode reloaded = reload(guide);
        assertThat(reloaded.get("blocks")).hasSize(1);
        assertThat(reloaded.get("blocks").get(0).get("sortOrder").asInt()).isZero();
    }

    @Test
    void blocksAreValidated() throws Exception {
        JsonNode guide = createGuide(PROJECT_A, "Highlights");
        Map<String, Object> badText = Map.of("blocks", List.of(Map.of("type", "heading", "level", 1, "text", "Big"),
                Map.of("type", "asset", "assetId", UUID.randomUUID().toString())));
        JsonNode invalid = addBlock(guide, Map.of("blockType", "TEXT", "translations", Map.of("el", Map.of("text", badText))), 422);
        assertThat(invalid.get("errors")).extracting(e -> e.get("path").asText())
                .contains("translations.el.text.blocks[0].level", "translations.el.text.blocks[1].assetId");

        assertThat(addBlock(guide, Map.of("blockType", "REFERENCE"), 422).get("errors").get(0).get("path").asText())
                .isEqualTo("resourceId");
        assertThat(addBlock(guide, Map.of("blockType", "REFERENCE", "resourceType", "PAGE",
                "resourceId", UUID.randomUUID().toString()), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("UNSUPPORTED_TYPE");

        JsonNode archived = createPoi(PROJECT_A, "{\"title\": \"Gone\"}");
        changeStatus(poiUrl(PROJECT_A), archived.get("id").asText(), "ARCHIVED", archived.get("version").asLong(), 200);
        assertThat(addBlock(guide, Map.of("blockType", "REFERENCE", "resourceType", "POI",
                "resourceId", archived.get("id").asText()), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("ARCHIVED_REFERENCE");
    }

    // --- Lifecycle and references ----------------------------------------------------------------

    @Test
    void activationNeedsTheTitleABlockAndDefaultLanguageText() throws Exception {
        JsonNode guide = createGuide(PROJECT_A, "Highlights");
        JsonNode empty = changeStatus(guidesUrl(PROJECT_A), guide.get("id").asText(), "ACTIVE", guide.get("version").asLong(), 422);
        assertThat(empty.get("errors").get(0).get("path").asText()).isEqualTo("blocks");

        addBlock(guide, Map.of("blockType", "TEXT", "translations",
                Map.of("en", Map.of("text", Map.of("blocks", List.of(Map.of("type", "paragraph", "text", "Only English")))))), 201);
        JsonNode noGreek = changeStatus(guidesUrl(PROJECT_A), guide.get("id").asText(), "ACTIVE", reload(guide).get("version").asLong(), 422);
        assertThat(noGreek.get("errors").get(0).get("path").asText()).isEqualTo("blocks[0].translations.el.text");

        JsonNode block = reload(guide).get("blocks").get(0);
        mockMvc.perform(json(put(blocksUrl(guide) + "/{b}", block.get("id").asText()),
                        Map.of("translations", Map.of("el", Map.of("text", STORY)))))
                .andExpect(status().isOk());
        changeStatus(guidesUrl(PROJECT_A), guide.get("id").asText(), "ACTIVE", reload(guide).get("version").asLong(), 200);
    }

    @Test
    void aPoiRouteAndPlaceAGuideReferencesAreInUse() throws Exception {
        JsonNode guide = createGuide(PROJECT_A, "Highlights");
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        JsonNode place = createPlace(PROJECT_A, "Acropolis", "SITE");
        JsonNode route = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes", PROJECT_A),
                        Map.of("title", "Trail", "type", "OUTDOOR"))).andExpect(status().isCreated()).andReturn());
        for (var target : List.of(Map.of("POI", poi), Map.of("PLACE", place), Map.of("ROUTE", route))) {
            var entry = target.entrySet().iterator().next();
            addBlock(guide, Map.of("blockType", "REFERENCE", "resourceType", entry.getKey(),
                    "resourceId", entry.getValue().get("id").asText()), 201);
        }

        JsonNode poiInUse = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ARCHIVED", poi.get("version").asLong(), 409);
        assertThat(poiInUse.get("references").get(0).get("resourceType").asText()).isEqualTo("GUIDE");
        assertThat(poiInUse.get("references").get(0).get("title").asText()).isEqualTo("Highlights");
        changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ARCHIVED", place.get("version").asLong(), 409);
        changeStatus("/api/v1/projects/" + PROJECT_A + "/routes", route.get("id").asText(), "ARCHIVED",
                route.get("version").asLong(), 409);

        changeStatus(guidesUrl(PROJECT_A), guide.get("id").asText(), "ARCHIVED", reload(guide).get("version").asLong(), 200);
        changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ARCHIVED", poi.get("version").asLong(), 200);
    }

    // --- Listing, order, and isolation -----------------------------------------------------------

    @Test
    void listsSearchesAndReorders() throws Exception {
        JsonNode first = createGuide(PROJECT_A, "Highlights");
        JsonNode second = createGuide(PROJECT_A, "For families");
        mockMvc.perform(authenticated(get(guidesUrl(PROJECT_A)).param("q", "famil")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.get("id").asText()));
        mockMvc.perform(json(put(guidesUrl(PROJECT_A) + ":order"),
                        Map.of("ids", List.of(second.get("id").asText(), first.get("id").asText()))))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(guidesUrl(PROJECT_A))))
                .andExpect(jsonPath("$.content[0].id").value(second.get("id").asText()));
    }

    @Test
    void anotherProjectsGuideTargetAndBlockAreInvisible() throws Exception {
        JsonNode foreignGuide = createGuide(PROJECT_B, "Elsewhere");
        JsonNode foreignBlock = addBlock(foreignGuide, Map.of("blockType", "TEXT"), 201);
        JsonNode foreignPoi = createPoi(PROJECT_B, "{\"title\": \"Far\"}");
        JsonNode own = createGuide(PROJECT_A, "Highlights");

        mockMvc.perform(authenticated(get(guidesUrl(PROJECT_A) + "/{id}", foreignGuide.get("id").asText())))
                .andExpect(status().isNotFound());
        assertThat(addBlock(own, Map.of("blockType", "REFERENCE", "resourceType", "POI",
                "resourceId", foreignPoi.get("id").asText()), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("RESOURCE_NOT_FOUND");
        mockMvc.perform(authenticated(delete(blocksUrl(own) + "/{b}", foreignBlock.get("id").asText())))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode createGuide(UUID projectId, String title) throws Exception {
        return read(mockMvc.perform(json(post(guidesUrl(projectId)), Map.of("title", title)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode addBlock(JsonNode guide, Map<String, Object> body, int expected) throws Exception {
        return read(mockMvc.perform(json(post(blocksUrl(guide)), body)).andExpect(status().is(expected)).andReturn());
    }

    private JsonNode reload(JsonNode guide) throws Exception {
        return read(mockMvc.perform(authenticated(get(guidesUrl(UUID.fromString(guide.get("projectId").asText()))
                        + "/{id}", guide.get("id").asText())))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode update(JsonNode guide, Map<String, Object> changes, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("version", guide.get("version").asLong());
        body.put("slug", guide.get("slug").asText());
        body.put("featured", false);
        body.putAll(changes);
        return read(mockMvc.perform(json(put(guidesUrl(PROJECT_A) + "/{id}", guide.get("id").asText()), body))
                .andExpect(status().is(expected)).andReturn());
    }

    private static String guidesUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/guides";
    }

    private static String blocksUrl(JsonNode guide) {
        return guidesUrl(UUID.fromString(guide.get("projectId").asText())) + "/" + guide.get("id").asText() + "/blocks";
    }
}
