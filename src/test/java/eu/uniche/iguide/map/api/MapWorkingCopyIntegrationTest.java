package eu.uniche.iguide.map.api;

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

/** The working copy of an ACTIVE Geographic Map, placements included ({@code 03} sections 1 and 7). */
class MapWorkingCopyIntegrationTest extends ContentIntegrationTest {

    private JsonNode acropolis;
    private JsonNode agora;
    private JsonNode plaka;

    @BeforeEach
    void setUp() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
        acropolis = createPlace(PROJECT_A, "Acropolis", "SITE");
        agora = createPlace(PROJECT_A, "Agora", "SITE");
        plaka = createPlace(PROJECT_A, "Plaka", "AREA");
    }

    @Test
    void aSaveLeavesTheLiveMapAndItsPlacementsUnchanged() throws Exception {
        JsonNode map = activeMapWith(acropolis, agora);
        List<JsonNode> live = placements(map);

        JsonNode copy = saveWorkingCopy(PROJECT_A, "maps", id(map), map.get("version").asLong(), null,
                payload("Athens old town", false, List.of(placement(live.get(0), 37.9716, 23.7262),
                        newPlacement(plaka, 37.9720, 23.7300))), 200);
        assertThat(copy.get("resource").get("internalName").asText()).isEqualTo("Athens old town");
        assertThat(copy.get("resource").get("hasWorkingCopy").asBoolean()).isTrue();
        assertThat(copy.get("resource").get("placements")).hasSize(2);
        assertThat(copy.get("payload").get("placements")).hasSize(2);

        mockMvc.perform(authenticated(get(mapsUrl(PROJECT_A) + "/{id}", id(map))))
                .andExpect(jsonPath("$.internalName").value("Athens centre"))
                .andExpect(jsonPath("$.visitorFilterEnabled").value(true))
                .andExpect(jsonPath("$.version").value(map.get("version").asLong()))
                .andExpect(jsonPath("$.hasWorkingCopy").value(true));
        assertThat(placements(map)).hasSize(2);
        assertThat(placements(map).stream().map(p -> p.get("resourceId").asText()))
                .containsExactlyInAnyOrder(id(acropolis), id(agora));
    }

    @Test
    void applyRewritesTheMapAndAddsMovesAndRemovesPlacements() throws Exception {
        JsonNode map = activeMapWith(acropolis, agora);
        JsonNode kept = placements(map).stream()
                .filter(p -> p.get("resourceId").asText().equals(id(acropolis))).findFirst().orElseThrow();

        saveWorkingCopy(PROJECT_A, "maps", id(map), map.get("version").asLong(), null,
                payload("Athens old town", false, List.of(placement(kept, 37.9716, 23.7262),
                        newPlacement(plaka, 37.9720, 23.7300))), 200);
        JsonNode applied = applyWorkingCopy(PROJECT_A, "maps", id(map), 200);
        assertThat(applied.get("internalName").asText()).isEqualTo("Athens old town");
        assertThat(applied.get("visitorFilterEnabled").asBoolean()).isFalse();
        assertThat(applied.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(applied.get("hasWorkingCopy").asBoolean()).isFalse();
        assertThat(applied.get("placements")).hasSize(2);

        List<JsonNode> now = placements(map);
        assertThat(now).hasSize(2);
        JsonNode moved = now.stream().filter(p -> p.get("id").asText().equals(id(kept))).findFirst().orElseThrow();
        assertThat(moved.get("latitude").decimalValue()).isEqualByComparingTo("37.9716");
        assertThat(now.stream().map(p -> p.get("resourceId").asText()))
                .containsExactlyInAnyOrder(id(acropolis), id(plaka));
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "maps", id(map)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void placementErrorsNameTheirPositionInThePayload() throws Exception {
        JsonNode map = activeMapWith(acropolis);
        long version = map.get("version").asLong();

        JsonNode error = saveWorkingCopy(PROJECT_A, "maps", id(map), version, null,
                payload("Athens centre", true, List.of(newPlacement(agora, 95, 23.72))), 422);
        assertThat(error.get("errors").get(0).get("path").asText()).isEqualTo("placements[0].latitude");

        JsonNode duplicate = saveWorkingCopy(PROJECT_A, "maps", id(map), version, null,
                payload("Athens centre", true, List.of(newPlacement(agora, 37.9, 23.7), newPlacement(agora, 37.8, 23.7))),
                409);
        assertThat(duplicate.get("errors").get(0).get("path").asText()).isEqualTo("placements[1].resourceId");

        JsonNode foreign = createPlace(PROJECT_B, "Knossos", "SITE");
        JsonNode notFound = saveWorkingCopy(PROJECT_A, "maps", id(map), version, null,
                payload("Athens centre", true, List.of(newPlacement(foreign, 37.9, 23.7))), 422);
        assertThat(notFound.get("errors").get(0).get("path").asText()).isEqualTo("placements[0].resourceId");
        assertThat(notFound.get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");

        mockMvc.perform(authenticated(get(mapsUrl(PROJECT_A) + "/{id}", id(map))))
                .andExpect(jsonPath("$.hasWorkingCopy").value(false));
    }

    @Test
    void theListFiltersPendingMapsAndDeactivationDropsTheCopy() throws Exception {
        JsonNode pending = activeMapWith(acropolis);
        activeMapWith();
        saveWorkingCopy(PROJECT_A, "maps", id(pending), pending.get("version").asLong(), null,
                payload("Athens old town", true, List.of()), 200);

        mockMvc.perform(authenticated(get(mapsUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id(pending)))
                .andExpect(jsonPath("$.content[0].hasWorkingCopy").value(true));
        mockMvc.perform(authenticated(get(mapsUrl(PROJECT_A))))
                .andExpect(jsonPath("$.totalElements").value(2));

        JsonNode draft = changeStatus(mapsUrl(PROJECT_A), id(pending), "DRAFT", pending.get("version").asLong(), 200);
        assertThat(draft.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "maps", id(pending)))))
                .andExpect(status().isNotFound());
        assertThat(placements(pending)).hasSize(1);
    }

    @Test
    void anotherProjectCannotSeeOrApplyTheCopy() throws Exception {
        JsonNode map = activeMapWith(acropolis);
        saveWorkingCopy(PROJECT_A, "maps", id(map), map.get("version").asLong(), null,
                payload("Athens old town", true, List.of()), 200);
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_B, "maps", id(map)))))
                .andExpect(status().isNotFound());
        applyWorkingCopy(PROJECT_B, "maps", id(map), 404);
    }

    // --- Helpers ---------------------------------------------------------------------------------

    /** An ACTIVE map of Athens with the given Places placed on it. */
    private JsonNode activeMapWith(JsonNode... places) throws Exception {
        JsonNode map = read(mockMvc.perform(json(post(mapsUrl(PROJECT_A)), Map.of("internalName", "Athens centre",
                        "initialLatitude", 37.97, "initialLongitude", 23.72, "initialZoom", 14)))
                .andExpect(status().isCreated()).andReturn());
        for (JsonNode place : places) {
            mockMvc.perform(json(post(mapsUrl(PROJECT_A) + "/{id}/resources", id(map)), Map.of("resourceType", "PLACE",
                            "resourceId", id(place), "latitude", 37.97, "longitude", 23.72)))
                    .andExpect(status().isCreated());
        }
        return changeStatus(mapsUrl(PROJECT_A), id(map), "ACTIVE", map.get("version").asLong(), 200);
    }

    private List<JsonNode> placements(JsonNode map) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        read(mockMvc.perform(authenticated(get(mapsUrl(PROJECT_A) + "/{id}/resources", id(map)))).andReturn())
                .forEach(result::add);
        return result;
    }

    private static Map<String, Object> payload(String name, boolean visitorFilter, List<Map<String, Object>> placements) {
        Map<String, Object> visible = Map.of("visible", true, "hiddenCategoryIds", List.of());
        Map<String, Object> payload = new HashMap<>(Map.of("internalName", name, "initialLatitude", 37.97,
                "initialLongitude", 23.72, "initialZoom", 15, "visibility", Map.of("POI", visible, "ROUTE", visible),
                "visitorFilterEnabled", visitorFilter));
        payload.put("placements", placements);
        return payload;
    }

    private static Map<String, Object> placement(JsonNode existing, double latitude, double longitude) {
        return Map.of("id", id(existing), "resourceType", existing.get("resourceType").asText(),
                "resourceId", existing.get("resourceId").asText(), "latitude", latitude, "longitude", longitude);
    }

    private static Map<String, Object> newPlacement(JsonNode place, double latitude, double longitude) {
        return Map.of("resourceType", "PLACE", "resourceId", id(place), "latitude", latitude, "longitude", longitude);
    }

    private static String mapsUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/maps";
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
