package eu.uniche.iguide.category.api;

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

/** Categories, their bindings to POIs and Routes, and map visibility ({@code 03} sections 7 and 10). */
class CategoryIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    // --- Create and update -----------------------------------------------------------------------

    @Test
    void createsADraftWithAGeneratedCodeAndSavesItsIconAndTexts() throws Exception {
        JsonNode category = createCategory(PROJECT_A, "Sculpture", "POI", null);
        assertThat(category.get("status").asText()).isEqualTo("DRAFT");
        assertThat(category.get("code").asText()).isEqualTo("sculpture");
        assertThat(category.get("resourceCount").asLong()).isZero();

        JsonNode saved = update(category, Map.of("icon", "statue", "translations",
                Map.of("en", Map.of("name", "Sculpture", "description", "Marble and bronze"))), 200);
        assertThat(saved.get("icon").asText()).isEqualTo("statue");
        assertThat(saved.get("translations").get("en").get("description").asText()).isEqualTo("Marble and bronze");

        assertThat(update(saved, Map.of("icon", "rocket"), 422).get("errors").get(0).get("path").asText()).isEqualTo("icon");
        mockMvc.perform(json(post(categoriesUrl(PROJECT_A)), Map.of("name", "Again", "code", "sculpture", "scope", "POI")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].path").value("code"));
    }

    @Test
    void twoLevelsOnlyWithTheParentsScope() throws Exception {
        JsonNode top = createCategory(PROJECT_A, "Sculpture", "POI", null);
        JsonNode sub = createCategory(PROJECT_A, "Kouroi", "POI", top.get("id").asText());
        assertThat(sub.get("parentCategoryId").asText()).isEqualTo(top.get("id").asText());

        mockMvc.perform(json(post(categoriesUrl(PROJECT_A)), Map.of("name", "Deeper", "scope", "POI",
                        "parentCategoryId", sub.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("TOO_DEEP"));
        mockMvc.perform(json(post(categoriesUrl(PROJECT_A)), Map.of("name", "Trails", "scope", "ROUTE",
                        "parentCategoryId", top.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("SCOPE_MISMATCH"));

        JsonNode other = createCategory(PROJECT_A, "Pottery", "POI", null);
        assertThat(update(top, Map.of("parentCategoryId", other.get("id").asText()), 422)
                .get("errors").get(0).get("code").asText()).isEqualTo("HAS_SUBCATEGORIES");
    }

    @Test
    void aScopeChangeMovesSubcategoriesButIsRefusedWhileResourcesAreIn() throws Exception {
        JsonNode top = createCategory(PROJECT_A, "Highlights", "POI", null);
        JsonNode sub = createCategory(PROJECT_A, "Short", "POI", top.get("id").asText());
        JsonNode moved = update(top, Map.of("scope", "ROUTE"), 200);
        assertThat(moved.get("scope").asText()).isEqualTo("ROUTE");
        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A) + "/{id}", sub.get("id").asText())))
                .andExpect(jsonPath("$.scope").value("ROUTE"));

        JsonNode pois = createCategory(PROJECT_A, "Sculpture", "POI", null);
        bind("pois", createPoi(PROJECT_A, "{\"title\": \"Kouros\"}"), pois, 201);
        assertThat(update(pois, Map.of("scope", "ROUTE"), 422).get("errors").get(0).get("code").asText()).isEqualTo("IN_USE");
    }

    // --- Lifecycle and references ----------------------------------------------------------------

    @Test
    void activationNeedsTheNameAndAnActiveParentAndRestoreANonArchivedParent() throws Exception {
        JsonNode top = createCategory(PROJECT_A, "Sculpture", "POI", null);
        JsonNode sub = createCategory(PROJECT_A, "Kouroi", "POI", top.get("id").asText());
        assertThat(changeStatus(categoriesUrl(PROJECT_A), sub.get("id").asText(), "ACTIVE", sub.get("version").asLong(), 422)
                .get("errors").get(0).get("path").asText()).isEqualTo("parentCategoryId");
        JsonNode activeTop = changeStatus(categoriesUrl(PROJECT_A), top.get("id").asText(), "ACTIVE", top.get("version").asLong(), 200);
        changeStatus(categoriesUrl(PROJECT_A), sub.get("id").asText(), "ACTIVE", sub.get("version").asLong(), 200);

        // A parent with live subcategories is in use.
        JsonNode inUse = changeStatus(categoriesUrl(PROJECT_A), top.get("id").asText(), "ARCHIVED",
                activeTop.get("version").asLong(), 409);
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("CATEGORY");
    }

    @Test
    void bindingsFollowScopeOneCategoryPerResourceAndKeepTheCategoryInUse() throws Exception {
        JsonNode sculpture = createCategory(PROJECT_A, "Sculpture", "POI", null);
        JsonNode pottery = createCategory(PROJECT_A, "Pottery", "POI", null);
        JsonNode trails = createCategory(PROJECT_A, "Trails", "ROUTE", null);
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");

        assertThat(bind("pois", poi, trails, 422).get("errors").get(0).get("code").asText()).isEqualTo("SCOPE_MISMATCH");
        JsonNode binding = bind("pois", poi, sculpture, 201);
        assertThat(bind("pois", poi, pottery, 409).get("type").asText()).isEqualTo("DUPLICATE_VALUE");

        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", poi.get("id").asText())))
                .andExpect(jsonPath("$.categoryIds[0]").value(sculpture.get("id").asText()));
        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A) + "/{id}", sculpture.get("id").asText())))
                .andExpect(jsonPath("$.resourceCount").value(1));

        JsonNode inUse = changeStatus(categoriesUrl(PROJECT_A), sculpture.get("id").asText(), "ARCHIVED",
                sculpture.get("version").asLong(), 409);
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("POI");
        assertThat(inUse.get("references").get(0).get("title").asText()).isEqualTo("Kouros");

        mockMvc.perform(authenticated(delete(poiUrl(PROJECT_A) + "/{p}/categories/{b}", poi.get("id").asText(),
                        binding.get("id").asText())))
                .andExpect(status().isNoContent());
        changeStatus(categoriesUrl(PROJECT_A), sculpture.get("id").asText(), "ARCHIVED", sculpture.get("version").asLong(), 200);
        assertThat(bind("pois", poi, sculpture, 422).get("errors").get(0).get("code").asText()).isEqualTo("ARCHIVED_REFERENCE");
    }

    @Test
    void routesAreCategorizedTooAndActiveResourcesKeepTheirCategory() throws Exception {
        JsonNode trails = createCategory(PROJECT_A, "Trails", "ROUTE", null);
        JsonNode route = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes", PROJECT_A),
                        Map.of("title", "Coast", "type", "OUTDOOR"))).andExpect(status().isCreated()).andReturn());
        bind("routes", route, trails, 201);
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/routes/{id}", PROJECT_A, route.get("id").asText())))
                .andExpect(jsonPath("$.categoryIds[0]").value(trails.get("id").asText()));

        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(), 200);
        JsonNode sculpture = createCategory(PROJECT_A, "Sculpture", "POI", null);
        assertThat(bind("pois", poi, sculpture, 422).get("errors").get(0).get("code").asText())
                .isEqualTo("RESOURCE_NOT_EDITABLE");
    }

    @Test
    void mapsHideOnlyCategoriesOfTheMatchingScope() throws Exception {
        JsonNode sculpture = createCategory(PROJECT_A, "Sculpture", "POI", null);
        JsonNode trails = createCategory(PROJECT_A, "Trails", "ROUTE", null);
        JsonNode map = read(mockMvc.perform(json(post("/api/v1/projects/{p}/maps", PROJECT_A), Map.of(
                        "internalName", "Athens", "initialLatitude", 37.97, "initialLongitude", 23.72, "initialZoom", 14)))
                .andExpect(status().isCreated()).andReturn());
        Map<String, Object> body = new HashMap<>(Map.of("version", map.get("version").asLong(), "internalName", "Athens",
                "initialLatitude", 37.97, "initialLongitude", 23.72, "initialZoom", 14, "visitorFilterEnabled", true));

        body.put("visibility", Map.of("POI", Map.of("visible", true, "hiddenCategoryIds", List.of(trails.get("id").asText())),
                "ROUTE", Map.of("visible", true, "hiddenCategoryIds", List.of())));
        mockMvc.perform(json(put("/api/v1/projects/{p}/maps/{m}", PROJECT_A, map.get("id").asText()), body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("SCOPE_MISMATCH"));

        body.put("visibility", Map.of("POI", Map.of("visible", true, "hiddenCategoryIds", List.of(sculpture.get("id").asText())),
                "ROUTE", Map.of("visible", true, "hiddenCategoryIds", List.of(trails.get("id").asText()))));
        mockMvc.perform(json(put("/api/v1/projects/{p}/maps/{m}", PROJECT_A, map.get("id").asText()), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility.ROUTE.hiddenCategoryIds[0]").value(trails.get("id").asText()));
    }

    // --- Listing, order, and isolation -----------------------------------------------------------

    @Test
    void listsByScopeSearchesAndReorders() throws Exception {
        JsonNode first = createCategory(PROJECT_A, "Sculpture", "POI", null);
        JsonNode second = createCategory(PROJECT_A, "Trails", "ROUTE", null);
        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A)).param("scope", "ROUTE")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.get("id").asText()));
        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A)).param("q", "SCULP")))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(json(put(categoriesUrl(PROJECT_A) + ":order"),
                        Map.of("ids", List.of(second.get("id").asText(), first.get("id").asText()))))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A))))
                .andExpect(jsonPath("$.content[0].id").value(second.get("id").asText()));
    }

    @Test
    void anotherProjectsCategoryAndPoiAreInvisible() throws Exception {
        JsonNode foreign = createCategory(PROJECT_B, "Elsewhere", "POI", null);
        JsonNode foreignPoi = createPoi(PROJECT_B, "{\"title\": \"Far\"}");
        JsonNode own = createCategory(PROJECT_A, "Sculpture", "POI", null);
        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A) + "/{id}", foreign.get("id").asText())))
                .andExpect(status().isNotFound());
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        assertThat(bind("pois", poi, foreign, 422).get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        mockMvc.perform(json(post(poiUrl(PROJECT_A) + "/{p}/categories", foreignPoi.get("id").asText()),
                        Map.of("categoryId", own.get("id").asText())))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post(categoriesUrl(PROJECT_A)), Map.of("name", "Sub", "scope", "POI",
                        "parentCategoryId", foreign.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode createCategory(UUID projectId, String name, String scope, String parentId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", name, "scope", scope));
        body.put("parentCategoryId", parentId);
        return read(mockMvc.perform(json(post(categoriesUrl(projectId)), body)).andExpect(status().isCreated()).andReturn());
    }

    private JsonNode bind(String kind, JsonNode resource, JsonNode category, int expected) throws Exception {
        return read(mockMvc.perform(json(post("/api/v1/projects/{p}/" + kind + "/{r}/categories",
                        PROJECT_A, resource.get("id").asText()), Map.of("categoryId", category.get("id").asText())))
                .andExpect(status().is(expected)).andReturn());
    }

    private JsonNode update(JsonNode category, Map<String, Object> changes, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("version", category.get("version").asLong());
        body.put("code", category.get("code").asText());
        body.put("scope", category.get("scope").asText());
        body.put("parentCategoryId", category.get("parentCategoryId").isNull() ? null : category.get("parentCategoryId").asText());
        body.putAll(changes);
        return read(mockMvc.perform(json(put(categoriesUrl(PROJECT_A) + "/{id}", category.get("id").asText()), body))
                .andExpect(status().is(expected)).andReturn());
    }

    private static String categoriesUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/categories";
    }
}
