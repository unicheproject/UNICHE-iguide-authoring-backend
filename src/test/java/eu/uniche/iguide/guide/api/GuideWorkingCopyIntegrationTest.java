package eu.uniche.iguide.guide.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The working copy of an ACTIVE Guide end to end against PostgreSQL ({@code 03} sections 1 and 11, {@code 07}
 * section 10 on INLINE rows of a working copy, {@code 08} Milestone 8).
 */
class GuideWorkingCopyIntegrationTest extends ContentIntegrationTest {

    private JsonNode guide;
    private JsonNode textBlock;
    private JsonNode referenceBlock;
    private JsonNode poi;
    private JsonNode image;

    @BeforeEach
    void setUp() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
        image = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        JsonNode draft = read(mockMvc.perform(json(post(guidesUrl(PROJECT_A)), Map.of("title", "Highlights")))
                .andExpect(status().isCreated()).andReturn());
        textBlock = addBlock(draft, Map.of("blockType", "TEXT", "translations",
                Map.of("el", Map.of("text", story(paragraph("Ένας κούρος"))))));
        referenceBlock = addBlock(draft, Map.of("blockType", "REFERENCE", "resourceType", "POI",
                "resourceId", id(poi), "translations", Map.of("en", Map.of("caption", "Look up"))));
        guide = changeStatus(guidesUrl(PROJECT_A), id(draft), "ACTIVE", reload(draft).get("version").asLong(), 200);
    }

    @Test
    void aSaveLeavesTheLiveGuideItsBlocksAndItsInlineMediaUnchanged() throws Exception {
        JsonNode copy = saveWorkingCopy(PROJECT_A, "guides", id(guide), guide.get("version").asLong(), null,
                rewrittenStory(), 200);
        JsonNode rendered = copy.get("resource");
        assertThat(rendered.get("translations").get("el").get("title").asText()).isEqualTo("Τα σημαντικότερα");
        assertThat(rendered.get("blocks")).hasSize(2);
        assertThat(rendered.get("blocks").get(0).get("resourceType").asText()).isEqualTo("PLACE");
        assertThat(rendered.get("blocks").get(1).get("id").asText()).isEqualTo(id(textBlock));
        assertThat(inlineIds(rendered)).containsExactly(id(image));

        JsonNode live = reload(guide);
        assertThat(live.get("version").asLong()).isEqualTo(guide.get("version").asLong());
        assertThat(live.get("translations").get("el").get("title").asText()).isEqualTo("Highlights");
        assertThat(blockIds(live)).containsExactly(id(textBlock), id(referenceBlock));
        assertThat(live.get("blocks").get(0).get("translations").get("el").get("text").get("blocks")).hasSize(1);
        assertThat(inlineIds(live)).isEmpty();
        assertThat(live.get("hasWorkingCopy").asBoolean()).isTrue();
    }

    @Test
    void applyRewritesFieldsBlocksAndTheirInlineMedia() throws Exception {
        saveWorkingCopy(PROJECT_A, "guides", id(guide), guide.get("version").asLong(), null, rewrittenStory(), 200);

        JsonNode applied = applyWorkingCopy(PROJECT_A, "guides", id(guide), 200);
        assertThat(applied.get("version").asLong()).isEqualTo(guide.get("version").asLong() + 1);
        assertThat(applied.get("estimatedReadingMinutes").asInt()).isEqualTo(9);
        assertThat(applied.get("translations").get("el").get("title").asText()).isEqualTo("Τα σημαντικότερα");
        assertThat(applied.get("hasWorkingCopy").asBoolean()).isFalse();

        JsonNode live = reload(guide);
        List<String> ids = blockIds(live);
        assertThat(ids).hasSize(2).doesNotContain(id(referenceBlock));
        assertThat(ids.get(1)).isEqualTo(id(textBlock));
        assertThat(live.get("blocks").get(0).get("sortOrder").asInt()).isZero();
        assertThat(live.get("blocks").get(1).get("sortOrder").asInt()).isEqualTo(1);
        assertThat(live.get("blocks").get(1).get("translations").get("el").get("text").get("blocks").get(1)
                .get("type").asText()).isEqualTo("asset");
        assertThat(inlineIds(live)).containsExactly(id(image));
    }

    @Test
    void mediaIsReplacedAndInlineRowsStayAfterIt() throws Exception {
        JsonNode hero = uploadAsset(PROJECT_A, "hero.png", "image/png", png(8, 8));
        Map<String, Object> payload = new HashMap<>(rewrittenStory());
        payload.put("media", List.of(Map.of("assetId", id(hero), "usage", "HERO_IMAGE")));
        saveWorkingCopy(PROJECT_A, "guides", id(guide), guide.get("version").asLong(), null, payload, 200);
        assertThat(reload(guide).get("media")).isEmpty();

        applyWorkingCopy(PROJECT_A, "guides", id(guide), 200);
        JsonNode media = reload(guide).get("media");
        assertThat(media).hasSize(2);
        assertThat(media.get(0).get("usage").asText()).isEqualTo("HERO_IMAGE");
        assertThat(media.get(0).get("sortOrder").asInt()).isZero();
        assertThat(media.get(1).get("usage").asText()).isEqualTo("INLINE");
        assertThat(media.get(1).get("assetId").asText()).isEqualTo(id(image));

        // A second copy that drops the hero and the image keeps neither.
        JsonNode current = reload(guide);
        Map<String, Object> plain = new HashMap<>(rewrittenStory());
        plain.put("blocks", List.of(Map.of("id", id(textBlock), "blockType", "TEXT",
                "translations", Map.of("el", Map.of("text", story(paragraph("Χωρίς εικόνες")))))));
        saveWorkingCopy(PROJECT_A, "guides", id(guide), current.get("version").asLong(), null, plain, 200);
        applyWorkingCopy(PROJECT_A, "guides", id(guide), 200);
        assertThat(reload(guide).get("media")).isEmpty();
        assertThat(blockIds(reload(guide))).containsExactly(id(textBlock));
    }

    @Test
    void blockErrorsCarryTheirPositionInThePayload() throws Exception {
        JsonNode foreignPoi = createPoi(PROJECT_B, "{\"title\": \"Foreign\"}");
        Map<String, Object> payload = new HashMap<>(rewrittenStory());
        payload.put("blocks", List.of(
                Map.of("id", id(textBlock), "blockType", "TEXT"),
                Map.of("blockType", "REFERENCE", "resourceType", "POI", "resourceId", id(foreignPoi))));
        JsonNode error = saveWorkingCopy(PROJECT_A, "guides", id(guide), guide.get("version").asLong(), null, payload, 422);
        assertThat(error.get("errors").get(0).get("path").asText()).isEqualTo("blocks[1].resourceId");
        assertThat(error.get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");

        payload.put("blocks", List.of(Map.of("id", id(textBlock), "blockType", "REFERENCE", "resourceType", "POI",
                "resourceId", id(poi))));
        error = saveWorkingCopy(PROJECT_A, "guides", id(guide), guide.get("version").asLong(), null, payload, 422);
        assertThat(error.get("errors").get(0).get("path").asText()).isEqualTo("blocks[0].blockType");
        assertThat(error.get("errors").get(0).get("code").asText()).isEqualTo("IMMUTABLE");

        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "guides", id(guide)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void theListFiltersPendingGuidesAndDeactivationDropsTheCopy() throws Exception {
        saveWorkingCopy(PROJECT_A, "guides", id(guide), guide.get("version").asLong(), null, rewrittenStory(), 200);
        mockMvc.perform(authenticated(get(guidesUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].hasWorkingCopy").value(true));

        JsonNode draft = changeStatus(guidesUrl(PROJECT_A), id(guide), "DRAFT", guide.get("version").asLong(), 200);
        assertThat(draft.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(guidesUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(blockIds(reload(guide))).containsExactly(id(textBlock), id(referenceBlock));
    }

    @Test
    void blockEndpointsOfAnActiveGuideRefuseAndPointToTheWorkingCopy() throws Exception {
        mockMvc.perform(json(post(guidesUrl(PROJECT_A) + "/" + id(guide) + "/blocks"),
                        Map.of("blockType", "TEXT", "translations", Map.of("el", Map.of("text", story(paragraph("x")))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    /** New title and minutes; a new reference to a Place first, the text block kept with an image, the POI dropped. */
    private Map<String, Object> rewrittenStory() throws Exception {
        JsonNode place = createPlaceOnce();
        Map<String, Object> payload = new HashMap<>();
        payload.put("slug", guide.get("slug").asText());
        payload.put("estimatedReadingMinutes", 9);
        payload.put("featured", true);
        payload.put("translations", Map.of("el", Map.of("title", "Τα σημαντικότερα")));
        payload.put("blocks", List.of(
                Map.of("blockType", "REFERENCE", "resourceType", "PLACE", "resourceId", id(place)),
                Map.of("id", id(textBlock), "blockType", "TEXT", "translations", Map.of("el", Map.of("text",
                        story(paragraph("Ένας κούρος"), Map.of("type", "asset", "assetId", id(image))))))));
        return payload;
    }

    private JsonNode place;

    private JsonNode createPlaceOnce() throws Exception {
        if (place == null) {
            place = createPlace(PROJECT_A, "Αίθουσα 1", "ROOM");
        }
        return place;
    }

    private JsonNode addBlock(JsonNode guide, Map<String, Object> body) throws Exception {
        return read(mockMvc.perform(json(post(guidesUrl(PROJECT_A) + "/" + id(guide) + "/blocks"), body))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode reload(JsonNode guide) throws Exception {
        return read(mockMvc.perform(authenticated(get(guidesUrl(PROJECT_A) + "/{id}", id(guide))))
                .andExpect(status().isOk()).andReturn());
    }

    private static List<String> blockIds(JsonNode guide) {
        List<String> ids = new ArrayList<>();
        guide.get("blocks").forEach(b -> ids.add(b.get("id").asText()));
        return ids;
    }

    private static List<String> inlineIds(JsonNode guide) {
        List<String> ids = new ArrayList<>();
        guide.get("media").forEach(m -> {
            if (m.get("usage").asText().equals("INLINE")) {
                ids.add(m.get("assetId").asText());
            }
        });
        return ids;
    }

    @SafeVarargs
    private static Map<String, Object> story(Map<String, Object>... nodes) {
        return Map.of("blocks", List.of(nodes));
    }

    private static Map<String, Object> paragraph(String text) {
        return Map.of("type", "paragraph", "text", text);
    }

    private static String guidesUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/guides";
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
