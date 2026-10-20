package eu.uniche.iguide.asset.api;

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

/** Media bindings of Resources and the INLINE rows of rich text ({@code 03} section 4, {@code 07} sections 10 and 14). */
class ResourceAssetIntegrationTest extends ContentIntegrationTest {

    private JsonNode image;
    private JsonNode audio;

    @BeforeEach
    void setUp() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
        image = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        audio = uploadAsset(PROJECT_A, "story.mp3", "audio/mpeg", new byte[]{1, 2, 3});
    }

    @Test
    void aPoiBindsMediaOfTheAcceptedTypesInOrderAndReadsThemBack() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        String url = poiUrl(PROJECT_A) + "/" + poi.get("id").asText() + "/resource-assets";
        JsonNode hero = bind(url, image, "HERO_IMAGE", null, 201);
        JsonNode narration = bind(url, audio, "AUDIO_NARRATION", "en", 201);
        JsonNode gallery = bind(url, uploadAsset(PROJECT_A, "detail.png", "image/png", png(8, 8)), "GALLERY_IMAGE", null, 201);

        assertThat(bind(url, audio, "HERO_IMAGE", null, 422).get("errors").get(0).get("code").asText())
                .isEqualTo("WRONG_ASSET_TYPE");
        assertThat(bind(url, image, "HERO_IMAGE", null, 409).get("type").asText()).isEqualTo("DUPLICATE_VALUE");
        assertThat(bind(url, audio, "AUDIO_NARRATION", "fr", 422).get("errors").get(0).get("code").asText())
                .isEqualTo("UNKNOWN_LANGUAGE");
        assertThat(bind(url, image, "INLINE", null, 422).get("errors").get(0).get("code").asText())
                .isEqualTo("NOT_ALLOWED");
        JsonNode model = uploadAsset(PROJECT_A, "vase.glb", "model/gltf-binary", new byte[]{1});
        assertThat(bind(url, model, "MODEL", null, 422).get("errors").get(0).get("code").asText())
                .isEqualTo("MODEL_POSTER_REQUIRED");

        mockMvc.perform(json(put(url + ":order"), Map.of("ids", List.of(gallery.get("id").asText(),
                        hero.get("id").asText(), narration.get("id").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usage").value("GALLERY_IMAGE"));
        JsonNode reloaded = read(mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", poi.get("id").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(reloaded.get("media")).extracting(m -> m.get("usage").asText())
                .containsExactly("GALLERY_IMAGE", "HERO_IMAGE", "AUDIO_NARRATION");
        assertThat(reloaded.get("media").get(2).get("languageCode").asText()).isEqualTo("en");

        mockMvc.perform(authenticated(delete(url + "/{b}", hero.get("id").asText()))).andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(url))).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void aModelNeedsItsPosterAndThePosterStaysWhileTheModelIsBound() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Vase\"}");
        String url = poiUrl(PROJECT_A) + "/" + poi.get("id").asText() + "/resource-assets";
        JsonNode poster = bind(url, image, "MODEL_POSTER", null, 201);
        bind(url, uploadAsset(PROJECT_A, "vase.glb", "model/gltf-binary", new byte[]{1}), "MODEL", null, 201);
        mockMvc.perform(authenticated(delete(url + "/{b}", poster.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("MODEL_POSTER_REQUIRED"));
    }

    @Test
    void bindingsNeedADraftResourceAndAnAssetOfTheProjectAndKeepTheAssetInUse() throws Exception {
        JsonNode foreign = uploadAsset(PROJECT_B, "other.png", "image/png", png(8, 8));
        JsonNode route = create("routes", "Old town");
        String url = "/api/v1/projects/" + PROJECT_A + "/routes/" + route.get("id").asText() + "/resource-assets";
        assertThat(bind(url, foreign, "HERO_IMAGE", null, 422).get("errors").get(0).get("code").asText())
                .isEqualTo("RESOURCE_NOT_FOUND");
        bind(url, image, "HERO_IMAGE", null, 201);

        JsonNode inUse = changeStatus(assetUrl(PROJECT_A), image.get("id").asText(), "ARCHIVED",
                image.get("version").asLong(), 409);
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("ROUTE");
        assertThat(inUse.get("references").get(0).get("title").asText()).isEqualTo("Old town");

        JsonNode event = create("events", "Night tour");
        String eventUrl = "/api/v1/projects/" + PROJECT_A + "/events/" + event.get("id").asText() + "/resource-assets";
        bind(eventUrl, image, "GALLERY_IMAGE", null, 201);
        JsonNode active = changeStatus("/api/v1/projects/" + PROJECT_A + "/events", event.get("id").asText(), "ACTIVE",
                event.get("version").asLong(), 200);
        assertThat(active.get("media")).hasSize(1);
        assertThat(bind(eventUrl, audio, "AUDIO_NARRATION", null, 422).get("errors").get(0).get("code").asText())
                .isEqualTo("RESOURCE_NOT_EDITABLE");

        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/places/{id}/resource-assets", PROJECT_A, UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/pois/{id}/resource-assets", PROJECT_B,
                        createPoi(PROJECT_A, "{\"title\": \"Mine\"}").get("id").asText())))
                .andExpect(status().isNotFound());
    }

    @Test
    void imagesInGuideTextBecomeInlineBindingsThatFollowEverySave() throws Exception {
        JsonNode guide = create("guides", "Highlights");
        String guideId = guide.get("id").asText();
        String blocks = "/api/v1/projects/" + PROJECT_A + "/guides/" + guideId + "/blocks";
        JsonNode other = uploadAsset(PROJECT_A, "detail.png", "image/png", png(8, 8));

        JsonNode block = read(mockMvc.perform(json(post(blocks), Map.of("blockType", "TEXT", "translations", Map.of(
                        "el", text(Map.of("type", "paragraph", "text", "Ένας κούρος"), asset(image)),
                        "en", text(asset(image), asset(other))))))
                .andExpect(status().isCreated()).andReturn());
        assertThat(block.get("translations").get("el").get("text").get("blocks").get(1).get("type").asText())
                .isEqualTo("asset");
        assertThat(inlineAssetIds(guideId)).containsExactlyInAnyOrder(image.get("id").asText(), other.get("id").asText());
        assertThat(bind("/api/v1/projects/" + PROJECT_A + "/guides/" + guideId + "/resource-assets", image,
                "INLINE", null, 422).get("errors").get(0).get("code").asText()).isEqualTo("NOT_ALLOWED");

        mockMvc.perform(json(put(blocks + "/{b}", block.get("id").asText()), Map.of("translations",
                        Map.of("en", text(Map.of("type", "paragraph", "text", "No images"))))))
                .andExpect(status().isOk());
        assertThat(inlineAssetIds(guideId)).containsExactly(image.get("id").asText());

        JsonNode foreign = uploadAsset(PROJECT_B, "other.png", "image/png", png(8, 8));
        mockMvc.perform(json(post(blocks), Map.of("blockType", "TEXT", "translations", Map.of("el", text(asset(foreign))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("translations.el.text.blocks[0].assetId"))
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));

        JsonNode inUse = changeStatus(assetUrl(PROJECT_A), image.get("id").asText(), "ARCHIVED",
                image.get("version").asLong(), 409);
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("GUIDE");

        mockMvc.perform(authenticated(delete(blocks + "/{b}", block.get("id").asText()))).andExpect(status().isNoContent());
        assertThat(inlineAssetIds(guideId)).isEmpty();
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode bind(String url, JsonNode asset, String usage, String languageCode, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("assetId", asset.get("id").asText());
        body.put("usage", usage);
        body.put("languageCode", languageCode);
        return read(mockMvc.perform(json(post(url), body)).andExpect(status().is(expected)).andReturn());
    }

    private JsonNode create(String kind, String title) throws Exception {
        Map<String, Object> body = kind.equals("routes") ? Map.of("title", title, "type", "OUTDOOR") : Map.of("title", title);
        return read(mockMvc.perform(json(post("/api/v1/projects/{p}/" + kind, PROJECT_A), body))
                .andExpect(status().isCreated()).andReturn());
    }

    private List<String> inlineAssetIds(String guideId) throws Exception {
        JsonNode guide = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/guides/{id}", PROJECT_A, guideId)))
                .andExpect(status().isOk()).andReturn());
        List<String> ids = new java.util.ArrayList<>();
        guide.get("media").forEach(m -> {
            if (m.get("usage").asText().equals("INLINE")) {
                ids.add(m.get("assetId").asText());
            }
        });
        return ids;
    }

    @SafeVarargs
    private static Map<String, Object> text(Map<String, Object>... nodes) {
        return Map.of("text", Map.of("blocks", List.of(nodes)));
    }

    private static Map<String, Object> asset(JsonNode asset) {
        return Map.of("type", "asset", "assetId", asset.get("id").asText());
    }
}
