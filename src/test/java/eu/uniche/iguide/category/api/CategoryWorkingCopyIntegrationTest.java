package eu.uniche.iguide.category.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The working copy of an ACTIVE Category ({@code 03} sections 1 and 10, DECISIONS.md D-027). */
class CategoryWorkingCopyIntegrationTest extends ContentIntegrationTest {

    private JsonNode category;

    @BeforeEach
    void setUp() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
        category = activate(createCategory(PROJECT_A, "Sculpture", "POI", null));
    }

    @Test
    void aSaveLeavesTheLiveCategoryUntouchedAndApplyWritesIt() throws Exception {
        long live = category.get("version").asLong();
        JsonNode copy = saveWorkingCopy(PROJECT_A, "categories", id(category), live, null,
                payload("sculpture", "POI", null, "statue", "Γλυπτική"), 200);
        assertThat(copy.get("resource").get("icon").asText()).isEqualTo("statue");
        assertThat(copy.get("resource").get("translations").get("el").get("name").asText()).isEqualTo("Γλυπτική");

        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A) + "/{id}", id(category))))
                .andExpect(jsonPath("$.icon").isEmpty())
                .andExpect(jsonPath("$.translations.el.name").value("Sculpture"))
                .andExpect(jsonPath("$.version").value(live))
                .andExpect(jsonPath("$.hasWorkingCopy").value(true))
                .andExpect(jsonPath("$.workingCopyUpdatedAt").isNotEmpty());

        JsonNode applied = applyWorkingCopy(PROJECT_A, "categories", id(category), 200);
        assertThat(applied.get("icon").asText()).isEqualTo("statue");
        assertThat(applied.get("translations").get("el").get("name").asText()).isEqualTo("Γλυπτική");
        assertThat(applied.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(applied.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "categories", id(category)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void theRulesOfASaveApplyToThePayload() throws Exception {
        long live = category.get("version").asLong();
        JsonNode icon = saveWorkingCopy(PROJECT_A, "categories", id(category), live, null,
                payload("sculpture", "POI", null, "rocket", "Γλυπτική"), 422);
        assertThat(icon.get("errors").get(0).get("path").asText()).isEqualTo("icon");

        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        mockMvc.perform(json(post("/api/v1/projects/{p}/pois/{r}/categories", PROJECT_A, id(poi)),
                        Map.of("categoryId", id(category))))
                .andExpect(status().isCreated());
        JsonNode inUse = saveWorkingCopy(PROJECT_A, "categories", id(category), live, null,
                payload("sculpture", "ROUTE", null, null, "Γλυπτική"), 422);
        assertThat(inUse.get("errors").get(0).get("path").asText()).isEqualTo("scope");
        assertThat(inUse.get("errors").get(0).get("code").asText()).isEqualTo("IN_USE");

        JsonNode foreignParent = createCategory(PROJECT_B, "Elsewhere", "POI", null);
        JsonNode foreign = saveWorkingCopy(PROJECT_A, "categories", id(category), live, null,
                payload("sculpture", "POI", id(foreignParent), null, "Γλυπτική"), 422);
        assertThat(foreign.get("errors").get(0).get("path").asText()).isEqualTo("parentCategoryId");

        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A) + "/{id}", id(category))))
                .andExpect(jsonPath("$.hasWorkingCopy").value(false));
    }

    @Test
    void aScopeChangeAppliedMovesTheSubcategories() throws Exception {
        JsonNode sub = activate(createCategory(PROJECT_A, "Kouroi", "POI", id(category)));
        saveWorkingCopy(PROJECT_A, "categories", id(category), category.get("version").asLong(), null,
                payload("sculpture", "ROUTE", null, null, "Sculpture"), 200);
        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A) + "/{id}", id(sub))))
                .andExpect(jsonPath("$.scope").value("POI"));

        applyWorkingCopy(PROJECT_A, "categories", id(category), 200);
        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A) + "/{id}", id(sub))))
                .andExpect(jsonPath("$.scope").value("ROUTE"));
    }

    @Test
    void thePendingFilterAndArchivalDropTheCopy() throws Exception {
        createCategory(PROJECT_A, "Pottery", "POI", null);
        saveWorkingCopy(PROJECT_A, "categories", id(category), category.get("version").asLong(), null,
                payload("sculpture", "POI", null, null, "Γλυπτική"), 200);

        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id(category)))
                .andExpect(jsonPath("$.content[0].hasWorkingCopy").value(true));
        mockMvc.perform(authenticated(get(categoriesUrl(PROJECT_B)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(0));

        JsonNode archived = changeStatus(categoriesUrl(PROJECT_A), id(category), "ARCHIVED",
                category.get("version").asLong(), 200);
        assertThat(archived.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "categories", id(category)))))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode activate(JsonNode draft) throws Exception {
        return changeStatus(categoriesUrl(PROJECT_A), id(draft), "ACTIVE", draft.get("version").asLong(), 200);
    }

    private static Map<String, Object> payload(String code, String scope, String parentId, String icon, String name) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", code);
        payload.put("scope", scope);
        payload.put("parentCategoryId", parentId);
        payload.put("icon", icon);
        payload.put("translations", Map.of("el", Map.of("name", name)));
        return payload;
    }

    private JsonNode createCategory(UUID projectId, String name, String scope, String parentId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", name, "scope", scope));
        body.put("parentCategoryId", parentId);
        return read(mockMvc.perform(json(post(categoriesUrl(projectId)), body)).andExpect(status().isCreated())
                .andReturn());
    }

    private static String categoriesUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/categories";
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
