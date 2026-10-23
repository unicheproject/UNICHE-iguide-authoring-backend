package eu.uniche.iguide.poi.api;

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
 * The working copy of an ACTIVE Point of Interest: fields, translations, its Category, and its media
 * ({@code 03} section 1, DECISIONS.md D-027).
 */
class PoiWorkingCopyIntegrationTest extends ContentIntegrationTest {

    private JsonNode poi;
    private JsonNode category;
    private JsonNode image;

    @BeforeEach
    void setUp() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
        JsonNode draft = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        poi = changeStatus(poiUrl(PROJECT_A), id(draft), "ACTIVE", draft.get("version").asLong(), 200);
        category = createCategory(PROJECT_A, "Sculpture", "POI");
        image = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(10, 10));
    }

    @Test
    void aSaveLeavesTheLivePoiUntouchedAndApplyWritesFieldsCategoryAndMedia() throws Exception {
        long live = poi.get("version").asLong();
        Map<String, Object> payload = payload("Κούρος της Αναβύσσου", List.of(id(category)),
                List.of(media(id(image), "HERO_IMAGE")));
        JsonNode copy = saveWorkingCopy(PROJECT_A, "pois", id(poi), live, null, payload, 200);
        assertThat(copy.get("resource").get("categoryIds").get(0).asText()).isEqualTo(id(category));
        assertThat(copy.get("resource").get("media").get(0).get("usage").asText()).isEqualTo("HERO_IMAGE");
        assertThat(copy.get("payload").get("media").get(0).get("assetId").asText()).isEqualTo(id(image));

        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", id(poi))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.el.title").value("Κούρος"))
                .andExpect(jsonPath("$.categoryIds").isEmpty())
                .andExpect(jsonPath("$.media").isEmpty())
                .andExpect(jsonPath("$.version").value(live))
                .andExpect(jsonPath("$.hasWorkingCopy").value(true));
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/pois/{id}/resource-assets", PROJECT_A, id(poi))))
                .andExpect(jsonPath("$").isEmpty());

        JsonNode applied = applyWorkingCopy(PROJECT_A, "pois", id(poi), 200);
        assertThat(applied.get("translations").get("el").get("title").asText()).isEqualTo("Κούρος της Αναβύσσου");
        assertThat(applied.get("estimatedVisitMinutes").asInt()).isEqualTo(12);
        assertThat(applied.get("categoryIds").get(0).asText()).isEqualTo(id(category));
        assertThat(applied.get("media").get(0).get("assetId").asText()).isEqualTo(id(image));
        assertThat(applied.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "pois", id(poi)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSecondApplyRemovesTheCategoryAndKeepsOrReordersMedia() throws Exception {
        JsonNode gallery = uploadAsset(PROJECT_A, "detail.png", "image/png", png(10, 10));
        saveWorkingCopy(PROJECT_A, "pois", id(poi), poi.get("version").asLong(), null,
                payload("Κούρος", List.of(id(category)), List.of(media(id(image), "HERO_IMAGE"),
                        media(id(gallery), "GALLERY_IMAGE"))), 200);
        JsonNode first = applyWorkingCopy(PROJECT_A, "pois", id(poi), 200);
        String heroBinding = first.get("media").get(0).get("id").asText();

        saveWorkingCopy(PROJECT_A, "pois", id(poi), first.get("version").asLong(), null,
                payload("Κούρος", List.of(), List.of(media(id(gallery), "GALLERY_IMAGE"),
                        media(id(image), "HERO_IMAGE"))), 200);
        JsonNode second = applyWorkingCopy(PROJECT_A, "pois", id(poi), 200);
        assertThat(second.get("categoryIds")).isEmpty();
        assertThat(second.get("media").get(0).get("usage").asText()).isEqualTo("GALLERY_IMAGE");
        assertThat(second.get("media").get(1).get("id").asText()).isEqualTo(heroBinding);
    }

    @Test
    void payloadErrorsCarryTheirPathsAndForeignIdsAreRejected() throws Exception {
        long live = poi.get("version").asLong();
        JsonNode routeCategory = createCategory(PROJECT_A, "Trails", "ROUTE");
        JsonNode foreignCategory = createCategory(PROJECT_B, "Elsewhere", "POI");
        JsonNode foreignImage = uploadAsset(PROJECT_B, "other.png", "image/png", png(10, 10));

        JsonNode scope = saveWorkingCopy(PROJECT_A, "pois", id(poi), live, null,
                payload("Κούρος", List.of(id(routeCategory)), List.of()), 422);
        assertThat(scope.get("errors").get(0).get("path").asText()).isEqualTo("categoryIds[0]");
        assertThat(scope.get("errors").get(0).get("code").asText()).isEqualTo("SCOPE_MISMATCH");

        JsonNode foreign = saveWorkingCopy(PROJECT_A, "pois", id(poi), live, null,
                payload("Κούρος", List.of(id(foreignCategory)), List.of()), 422);
        assertThat(foreign.get("errors").get(0).get("path").asText()).isEqualTo("categoryIds[0]");
        assertThat(foreign.get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");

        JsonNode foreignMedia = saveWorkingCopy(PROJECT_A, "pois", id(poi), live, null,
                payload("Κούρος", List.of(), List.of(media(id(image), "HERO_IMAGE"),
                        media(id(foreignImage), "GALLERY_IMAGE"))), 422);
        assertThat(foreignMedia.get("errors").get(0).get("path").asText()).startsWith("media[1].");

        JsonNode wrongType = saveWorkingCopy(PROJECT_A, "pois", id(poi), live, null,
                payload("Κούρος", List.of(), List.of(media(id(image), "AUDIO_NARRATION"))), 422);
        assertThat(wrongType.get("errors").get(0).get("path").asText()).startsWith("media[0]");

        JsonNode tooMany = saveWorkingCopy(PROJECT_A, "pois", id(poi), live, null,
                payload("Κούρος", List.of(id(category), id(category)), List.of()), 422);
        assertThat(tooMany.get("errors").get(0).get("path").asText()).isEqualTo("categoryIds");

        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", id(poi))))
                .andExpect(jsonPath("$.hasWorkingCopy").value(false));
    }

    @Test
    void thePendingFilterAndDeactivationDropTheCopy() throws Exception {
        JsonNode other = createPoi(PROJECT_A, "{\"title\": \"Κόρη\"}");
        saveWorkingCopy(PROJECT_A, "pois", id(poi), poi.get("version").asLong(), null,
                payload("Κούρος", List.of(), List.of()), 200);

        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id(poi)))
                .andExpect(jsonPath("$.content[0].hasWorkingCopy").value(true));
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A))))
                .andExpect(jsonPath("$.content[?(@.id == '" + id(other) + "')].hasWorkingCopy").value(false));

        JsonNode draft = changeStatus(poiUrl(PROJECT_A), id(poi), "DRAFT", poi.get("version").asLong(), 200);
        assertThat(draft.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void directChildWritesOnAnActivePoiStayRefused() throws Exception {
        mockMvc.perform(json(post("/api/v1/projects/{p}/pois/{id}/categories", PROJECT_A, id(poi)),
                        Map.of("categoryId", id(category))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
        mockMvc.perform(json(post("/api/v1/projects/{p}/pois/{id}/resource-assets", PROJECT_A, id(poi)),
                        Map.of("assetId", id(image), "usage", "HERO_IMAGE")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private Map<String, Object> payload(String title, List<String> categoryIds, List<Map<String, Object>> media) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("slug", poi.get("slug").asText());
        payload.put("estimatedVisitMinutes", 12);
        payload.put("featured", false);
        payload.put("translations", Map.of("el", Map.of("title", title)));
        payload.put("categoryIds", new ArrayList<>(categoryIds));
        payload.put("media", media);
        return payload;
    }

    private static Map<String, Object> media(String assetId, String usage) {
        Map<String, Object> media = new HashMap<>();
        media.put("assetId", assetId);
        media.put("usage", usage);
        media.put("languageCode", null);
        return media;
    }

    private JsonNode createCategory(UUID projectId, String name, String scope) throws Exception {
        return read(mockMvc.perform(json(post("/api/v1/projects/{p}/categories", projectId),
                        Map.of("name", name, "scope", scope)))
                .andExpect(status().isCreated()).andReturn());
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
