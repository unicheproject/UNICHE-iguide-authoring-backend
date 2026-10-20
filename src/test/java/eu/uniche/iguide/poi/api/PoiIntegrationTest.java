package eu.uniche.iguide.poi.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Points of Interest end to end against PostgreSQL ({@code 03} section 6, {@code 08} Milestone 4). */
class PoiIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    // --- Create and read -------------------------------------------------------------------------

    @Test
    void createsADraftWithItsTitleACodeAPlaceAndAGeneratedSlug() throws Exception {
        JsonNode museum = createPlace(PROJECT_A, "Museum", "BUILDING");
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros of Melos\", \"code\": \" 12 \", \"placeId\": \""
                + museum.get("id").asText() + "\"}");
        assertThat(poi.get("status").asText()).isEqualTo("DRAFT");
        assertThat(poi.get("slug").asText()).isEqualTo("kouros-of-melos");
        assertThat(poi.get("code").asText()).isEqualTo("12");
        assertThat(poi.get("placeId").asText()).isEqualTo(museum.get("id").asText());
        assertThat(poi.get("translations").get("el").get("title").asText()).isEqualTo("Kouros of Melos");

        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", poi.get("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("12"));
    }

    @Test
    void codesAndSlugsAreUniquePerProjectButNotAcrossProjects() throws Exception {
        createPoi(PROJECT_A, "{\"title\": \"Kouros\", \"code\": \"12\", \"slug\": \"kouros\"}");
        mockMvc.perform(json(post(poiUrl(PROJECT_A)), "{\"title\": \"Kore\", \"code\": \"12\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("DUPLICATE_VALUE"))
                .andExpect(jsonPath("$.errors[0].path").value("code"));
        mockMvc.perform(json(post(poiUrl(PROJECT_A)), "{\"title\": \"Kore\", \"slug\": \"kouros\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].path").value("slug"));
        createPoi(PROJECT_B, "{\"title\": \"Kouros\", \"code\": \"12\", \"slug\": \"kouros\"}");
    }

    @Test
    void rejectsAMalformedCodeAnArchivedPlaceAndAMissingTitleTogether() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Old wing", "AREA");
        changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ARCHIVED", place.get("version").asLong(), 200);
        mockMvc.perform(json(post(poiUrl(PROJECT_A)), "{\"title\": \"\", \"code\": \"12 A!\", \"placeId\": \""
                        + place.get("id").asText() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.path == 'title')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.path == 'code')].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[?(@.path == 'placeId')].code").value("ARCHIVED_REFERENCE"));
    }

    // --- Update ----------------------------------------------------------------------------------

    @Test
    void savesADraftWithTranslationsCoordinatesAndVisitMinutes() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode saved = update(poi, Map.of("estimatedVisitMinutes", 15, "translations", Map.of(
                "el", Map.of("title", "Κούρος", "instructions", "Κοιτάξτε από αριστερά"),
                "en", Map.of("title", "Kouros", "accessibilityInformation", "Step-free"))), 200);
        assertThat(saved.get("estimatedVisitMinutes").asInt()).isEqualTo(15);
        assertThat(saved.get("translations").get("en").get("accessibilityInformation").asText()).isEqualTo("Step-free");
        assertThat(saved.get("translations").get("el").get("instructions").asText()).isEqualTo("Κοιτάξτε από αριστερά");
        assertThat(saved.get("version").asLong()).isGreaterThan(poi.get("version").asLong());
    }

    @Test
    void rejectsVisitMinutesOutOfRangeAndAStaleVersion() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        update(poi, Map.of("estimatedVisitMinutes", 0), 422);
        update(poi, Map.of(), 200);
        JsonNode conflict = update(poi, Map.of(), 409);
        assertThat(conflict.get("type").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(conflict.get("currentVersion").asLong()).isEqualTo(poi.get("version").asLong() + 1);
    }

    @Test
    void anActivePoiIsNotEditedInPlace() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        JsonNode active = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(), 200);
        JsonNode error = update(active, Map.of(), 422);
        assertThat(error.get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_EDITABLE");
    }

    // --- Lifecycle and references ----------------------------------------------------------------

    @Test
    void activationNeedsTheDefaultLanguageTitleAndOnlyDraftLeavesArchived() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode untitled = update(poi, Map.of("translations", Map.of("el", Map.of("title", " "))), 200);
        JsonNode missing = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", untitled.get("version").asLong(), 422);
        assertThat(missing.get("errors").get(0).get("path").asText()).isEqualTo("translations.el.title");

        JsonNode archived = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ARCHIVED", untitled.get("version").asLong(), 200);
        changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", archived.get("version").asLong(), 422);
        changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "DRAFT", archived.get("version").asLong(), 200);
    }

    @Test
    void aPoiPlacedOnAMapOrAFloorPlanIsInUse() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        JsonNode map = read(mockMvc.perform(json(post("/api/v1/projects/{p}/maps", PROJECT_A), Map.of(
                        "internalName", "Athens", "initialLatitude", 37.97, "initialLongitude", 23.72, "initialZoom", 14)))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post("/api/v1/projects/{p}/maps/{m}/resources", PROJECT_A, map.get("id").asText()),
                        Map.of("resourceType", "POI", "resourceId", poi.get("id").asText(), "latitude", 37.97, "longitude", 23.72)))
                .andExpect(status().isCreated());
        JsonNode plan = read(mockMvc.perform(json(post("/api/v1/projects/{p}/floor-plans", PROJECT_A),
                        Map.of("internalName", "Ground floor")))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post("/api/v1/projects/{p}/floor-plans/{f}/positions", PROJECT_A, plan.get("id").asText()),
                        Map.of("resourceType", "POI", "resourceId", poi.get("id").asText(), "x", 0.4, "y", 0.6)))
                .andExpect(status().isCreated());

        JsonNode inUse = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ARCHIVED", poi.get("version").asLong(), 409);
        assertThat(inUse.get("type").asText()).isEqualTo("RESOURCE_IN_USE");
        assertThat(inUse.get("references")).extracting(r -> r.get("resourceType").asText())
                .containsExactlyInAnyOrder("MAP", "FLOOR_PLAN");
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}/references", poi.get("id").asText())))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void archivedPoisCannotBePlaced() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ARCHIVED", poi.get("version").asLong(), 200);
        JsonNode plan = read(mockMvc.perform(json(post("/api/v1/projects/{p}/floor-plans", PROJECT_A),
                        Map.of("internalName", "Ground floor")))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post("/api/v1/projects/{p}/floor-plans/{f}/positions", PROJECT_A, plan.get("id").asText()),
                        Map.of("resourceType", "POI", "resourceId", poi.get("id").asText(), "x", 0.4, "y", 0.6)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("ARCHIVED_REFERENCE"));
    }

    // --- Listing and order -----------------------------------------------------------------------

    @Test
    void listsInOrderAndFiltersByPlaceTitleAndExactCode() throws Exception {
        JsonNode museum = createPlace(PROJECT_A, "Museum", "BUILDING");
        JsonNode kouros = createPoi(PROJECT_A, "{\"title\": \"Kouros\", \"code\": \"12\", \"placeId\": \""
                + museum.get("id").asText() + "\"}");
        JsonNode kore = createPoi(PROJECT_A, "{\"title\": \"Kore\", \"code\": \"120\"}");

        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A))))
                .andExpect(jsonPath("$.content[0].id").value(kouros.get("id").asText()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A)).param("placeId", museum.get("id").asText())))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A)).param("q", "12")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(kouros.get("id").asText()));
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A)).param("q", "KOR")))
                .andExpect(jsonPath("$.content[0].id").value(kore.get("id").asText()));

        mockMvc.perform(json(put(poiUrl(PROJECT_A) + ":order"),
                        Map.of("ids", List.of(kore.get("id").asText(), kouros.get("id").asText()))))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A))))
                .andExpect(jsonPath("$.content[0].id").value(kore.get("id").asText()));
    }

    // --- Cross-Project isolation -----------------------------------------------------------------

    @Test
    void anotherProjectsPoiAndPlaceAreInvisible() throws Exception {
        JsonNode foreignPoi = createPoi(PROJECT_B, "{\"title\": \"Elsewhere\"}");
        JsonNode foreignPlace = createPlace(PROJECT_B, "Elsewhere", "SITE");
        JsonNode ownMap = read(mockMvc.perform(json(post("/api/v1/projects/{p}/maps", PROJECT_A), Map.of(
                        "internalName", "Athens", "initialLatitude", 37.97, "initialLongitude", 23.72, "initialZoom", 14)))
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", foreignPoi.get("id").asText())))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post(poiUrl(PROJECT_A)), "{\"title\": \"Kouros\", \"placeId\": \""
                        + foreignPlace.get("id").asText() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(json(post("/api/v1/projects/{p}/maps/{m}/resources", PROJECT_A, ownMap.get("id").asText()),
                        Map.of("resourceType", "POI", "resourceId", foreignPoi.get("id").asText(), "latitude", 37.9, "longitude", 23.7)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(json(put(poiUrl(PROJECT_A) + ":order"), Map.of("ids", List.of(foreignPoi.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode update(JsonNode poi, Map<String, Object> changes, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("version", poi.get("version").asLong());
        body.put("slug", poi.get("slug").asText());
        body.put("code", poi.get("code").isNull() ? null : poi.get("code").asText());
        body.put("latitude", 37.9715);
        body.put("longitude", 23.7257);
        body.put("featured", false);
        body.putAll(changes);
        return read(mockMvc.perform(json(put(poiUrl(PROJECT_A) + "/{id}", poi.get("id").asText()), body))
                .andExpect(status().is(expected)).andReturn());
    }
}
