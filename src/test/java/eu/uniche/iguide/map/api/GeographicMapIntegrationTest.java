package eu.uniche.iguide.map.api;

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

/** Geographic Maps and their placements ({@code 03} section 7, {@code 08} Milestone 5). */
class GeographicMapIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    @Test
    void createsADraftWithEverythingVisibleAndTheVisitorFilterOn() throws Exception {
        JsonNode map = createMap(PROJECT_A, Map.of());
        assertThat(map.get("status").asText()).isEqualTo("DRAFT");
        assertThat(map.get("visibility").get("POI").get("visible").asBoolean()).isTrue();
        assertThat(map.get("visibility").get("ROUTE").get("visible").asBoolean()).isTrue();
        assertThat(map.get("visitorFilterEnabled").asBoolean()).isTrue();
        assertThat(map.get("area").asText()).isEqualTo("Athens");
    }

    @Test
    void boundsAreAllOrNoneAndContainTheInitialView() throws Exception {
        mockMvc.perform(json(post(mapsUrl(PROJECT_A)), mapBody(Map.of("minLatitude", 37.9))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INCOMPLETE"));
        mockMvc.perform(json(post(mapsUrl(PROJECT_A)), mapBody(Map.of("minLatitude", 38.0, "maxLatitude", 38.1,
                        "minLongitude", 23.7, "maxLongitude", 23.8))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("OUTSIDE_BOUNDS"));
        mockMvc.perform(json(post(mapsUrl(PROJECT_A)), mapBody(Map.of("initialZoom", 23))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("initialZoom"));
    }

    @Test
    void aMapLinksOnlyAnOutdoorPlaceAndAPlaceShowsOneMap() throws Exception {
        JsonNode indoor = createPlace(PROJECT_A, "Museum", "BUILDING");
        mockMvc.perform(json(post(mapsUrl(PROJECT_A)), mapBody(Map.of("placeId", indoor.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("ENVIRONMENT_MISMATCH"));

        JsonNode outdoor = createPlace(PROJECT_A, "Acropolis", "SITE");
        createMap(PROJECT_A, Map.of("placeId", outdoor.get("id").asText()));
        mockMvc.perform(json(post(mapsUrl(PROJECT_A)), mapBody(Map.of("placeId", outdoor.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("ALREADY_LINKED"));
    }

    @Test
    void aLinkedOrPlacedPlaceCannotBeArchivedNorChangeEnvironment() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Acropolis", "SITE");
        JsonNode map = createMap(PROJECT_A, Map.of("placeId", place.get("id").asText()));

        JsonNode inUse = changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ARCHIVED",
                place.get("version").asLong(), 409);
        assertThat(inUse.get("type").asText()).isEqualTo("RESOURCE_IN_USE");
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("MAP");
        assertThat(inUse.get("references").get(0).get("resourceId").asText()).isEqualTo(map.get("id").asText());

        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A) + "/{id}/references", place.get("id").asText())))
                .andExpect(jsonPath("$[0].title").value("Athens centre"));

        Map<String, Object> indoor = new HashMap<>(Map.of("version", place.get("version").asLong(),
                "slug", place.get("slug").asText(), "type", "SITE", "environment", "INDOOR", "featured", false));
        mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", place.get("id").asText()), indoor))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("LINKED"));

        // Archiving the map frees the Place.
        changeStatus(mapsUrl(PROJECT_A), map.get("id").asText(), "ARCHIVED", map.get("version").asLong(), 200);
        changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ARCHIVED", place.get("version").asLong(), 200);
    }

    @Test
    void placesAndMovesAndRemovesAPlaceOnADraftMap() throws Exception {
        JsonNode map = createMap(PROJECT_A, Map.of());
        JsonNode place = createPlace(PROJECT_A, "Acropolis", "SITE");
        JsonNode placement = read(mockMvc.perform(json(post(mapsUrl(PROJECT_A) + "/{id}/resources", map.get("id").asText()),
                        Map.of("resourceType", "PLACE", "resourceId", place.get("id").asText(),
                                "latitude", 37.9715, "longitude", 23.7257)))
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(json(post(mapsUrl(PROJECT_A) + "/{id}/resources", map.get("id").asText()),
                        Map.of("resourceType", "PLACE", "resourceId", place.get("id").asText(),
                                "latitude", 37.9, "longitude", 23.7)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("DUPLICATE_VALUE"));

        mockMvc.perform(json(put(mapsUrl(PROJECT_A) + "/{id}/resources/{r}", map.get("id").asText(),
                        placement.get("id").asText()), Map.of("latitude", 37.97, "longitude", 23.72)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(37.97));

        // A placed Place is in use as well.
        changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ARCHIVED", place.get("version").asLong(), 409);

        mockMvc.perform(authenticated(delete(mapsUrl(PROJECT_A) + "/{id}/resources/{r}", map.get("id").asText(),
                        placement.get("id").asText())))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(mapsUrl(PROJECT_A) + "/{id}/resources", map.get("id").asText())))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void placementsRejectUnsupportedTypesArchivedTargetsAndAnActiveMap() throws Exception {
        JsonNode map = createMap(PROJECT_A, Map.of());
        mockMvc.perform(json(post(mapsUrl(PROJECT_A) + "/{id}/resources", map.get("id").asText()),
                        Map.of("resourceType", "ROUTE", "resourceId", UUID.randomUUID().toString(),
                                "latitude", 37.9, "longitude", 23.7)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED_TYPE"));
        mockMvc.perform(json(post(mapsUrl(PROJECT_A) + "/{id}/resources", map.get("id").asText()),
                        Map.of("resourceType", "POI", "resourceId", UUID.randomUUID().toString(),
                                "latitude", 37.9, "longitude", 23.7)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));

        JsonNode archived = createPlace(PROJECT_A, "Gone", "SITE");
        changeStatus(placeUrl(PROJECT_A), archived.get("id").asText(), "ARCHIVED", archived.get("version").asLong(), 200);
        mockMvc.perform(json(post(mapsUrl(PROJECT_A) + "/{id}/resources", map.get("id").asText()),
                        Map.of("resourceType", "PLACE", "resourceId", archived.get("id").asText(),
                                "latitude", 37.9, "longitude", 23.7)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("ARCHIVED_REFERENCE"));

        JsonNode active = changeStatus(mapsUrl(PROJECT_A), map.get("id").asText(), "ACTIVE", map.get("version").asLong(), 200);
        JsonNode place = createPlace(PROJECT_A, "Acropolis", "SITE");
        mockMvc.perform(json(post(mapsUrl(PROJECT_A) + "/{id}/resources", active.get("id").asText()),
                        Map.of("resourceType", "PLACE", "resourceId", place.get("id").asText(),
                                "latitude", 37.9, "longitude", 23.7)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
    }

    @Test
    void savesVisibilityAndRejectsHiddenCategoriesUntilCategoriesExist() throws Exception {
        JsonNode map = createMap(PROJECT_A, Map.of());
        Map<String, Object> update = new HashMap<>(mapBody(Map.of()));
        update.put("version", map.get("version").asLong());
        update.put("visitorFilterEnabled", false);
        update.put("visibility", Map.of("POI", Map.of("visible", false, "hiddenCategoryIds", List.of()),
                "ROUTE", Map.of("visible", true, "hiddenCategoryIds", List.of())));
        JsonNode saved = read(mockMvc.perform(json(put(mapsUrl(PROJECT_A) + "/{id}", map.get("id").asText()), update))
                .andExpect(status().isOk()).andReturn());
        assertThat(saved.get("visibility").get("POI").get("visible").asBoolean()).isFalse();
        assertThat(saved.get("visitorFilterEnabled").asBoolean()).isFalse();

        update.put("version", saved.get("version").asLong());
        update.put("visibility", Map.of("POI", Map.of("visible", true, "hiddenCategoryIds",
                List.of(UUID.randomUUID().toString())), "ROUTE", Map.of("visible", true, "hiddenCategoryIds", List.of())));
        mockMvc.perform(json(put(mapsUrl(PROJECT_A) + "/{id}", map.get("id").asText()), update))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("visibility.POI.hiddenCategoryIds"));
    }

    // --- Cross-Project isolation -----------------------------------------------------------------

    @Test
    void anotherProjectsMapPlaceAndPlacementAreInvisible() throws Exception {
        JsonNode foreignMap = createMap(PROJECT_B, Map.of());
        JsonNode foreignPlace = createPlace(PROJECT_B, "Elsewhere", "SITE");
        JsonNode ownMap = createMap(PROJECT_A, Map.of());

        mockMvc.perform(authenticated(get(mapsUrl(PROJECT_A) + "/{id}", foreignMap.get("id").asText())))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post(mapsUrl(PROJECT_A)), mapBody(Map.of("placeId", foreignPlace.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(json(post(mapsUrl(PROJECT_A) + "/{id}/resources", ownMap.get("id").asText()),
                        Map.of("resourceType", "PLACE", "resourceId", foreignPlace.get("id").asText(),
                                "latitude", 37.9, "longitude", 23.7)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));

        JsonNode foreignPlacement = read(mockMvc.perform(json(post(mapsUrl(PROJECT_B) + "/{id}/resources",
                        foreignMap.get("id").asText()), Map.of("resourceType", "PLACE",
                        "resourceId", foreignPlace.get("id").asText(), "latitude", 37.9, "longitude", 23.7)))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(authenticated(delete(mapsUrl(PROJECT_A) + "/{id}/resources/{r}", ownMap.get("id").asText(),
                        foreignPlacement.get("id").asText())))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode createMap(UUID projectId, Map<String, Object> changes) throws Exception {
        return read(mockMvc.perform(json(post(mapsUrl(projectId)), mapBody(changes)))
                .andExpect(status().isCreated()).andReturn());
    }

    private static Map<String, Object> mapBody(Map<String, Object> changes) {
        Map<String, Object> body = new HashMap<>(Map.of("internalName", "Athens centre", "initialLatitude", 37.97,
                "initialLongitude", 23.72, "initialZoom", 14, "area", "Athens"));
        body.putAll(changes);
        return body;
    }

    private static String mapsUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/maps";
    }
}
