package eu.uniche.iguide.floorplan.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** The working copy of an ACTIVE Floor Plan, positions included ({@code 03} sections 1 and 8). */
class FloorPlanWorkingCopyIntegrationTest extends ContentIntegrationTest {

    private JsonNode museum;
    private JsonNode room1;
    private JsonNode room2;
    private JsonNode room3;
    private JsonNode image;

    @BeforeEach
    void setUp() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
        museum = createPlace(PROJECT_A, "Museum", "BUILDING");
        room1 = createPlace(PROJECT_A, "Room 1", "ROOM");
        room2 = createPlace(PROJECT_A, "Room 2", "ROOM");
        room3 = createPlace(PROJECT_A, "Room 3", "ROOM");
        image = uploadAsset(PROJECT_A, "ground.png", "image/png", png(20, 14), "type", "FLOOR_PLAN");
    }

    @Test
    void aSaveLeavesTheLivePlanAndItsPositionsUnchanged() throws Exception {
        JsonNode plan = activePlanWith(room1, room2);

        JsonNode copy = saveWorkingCopy(PROJECT_A, "floor-plans", id(plan), plan.get("version").asLong(), null,
                payload("Ground floor (new)", id(image), List.of(newPosition(room3, 0.1, 0.2))), 200);
        assertThat(copy.get("resource").get("internalName").asText()).isEqualTo("Ground floor (new)");
        assertThat(copy.get("resource").get("placeId").asText()).isEqualTo(id(museum));
        assertThat(copy.get("resource").get("positions")).hasSize(1);

        mockMvc.perform(authenticated(get(plansUrl(PROJECT_A) + "/{id}", id(plan))))
                .andExpect(jsonPath("$.internalName").value("Ground floor"))
                .andExpect(jsonPath("$.placeId").isEmpty())
                .andExpect(jsonPath("$.hasWorkingCopy").value(true));
        assertThat(positions(plan)).hasSize(2);
    }

    @Test
    void applyRewritesThePlanAndAddsMovesAndRemovesPositions() throws Exception {
        JsonNode plan = activePlanWith(room1, room2);
        JsonNode kept = positions(plan).stream()
                .filter(p -> p.get("resourceId").asText().equals(id(room1))).findFirst().orElseThrow();

        saveWorkingCopy(PROJECT_A, "floor-plans", id(plan), plan.get("version").asLong(), null,
                payload("Ground floor (new)", id(image), List.of(position(kept, 0.9, 0.8, 90),
                        newPosition(room3, 0.1, 0.2))), 200);
        JsonNode applied = applyWorkingCopy(PROJECT_A, "floor-plans", id(plan), 200);
        assertThat(applied.get("internalName").asText()).isEqualTo("Ground floor (new)");
        assertThat(applied.get("placeId").asText()).isEqualTo(id(museum));
        assertThat(applied.get("hasWorkingCopy").asBoolean()).isFalse();
        assertThat(applied.get("positions")).hasSize(2);

        List<JsonNode> now = positions(plan);
        JsonNode moved = now.stream().filter(p -> p.get("id").asText().equals(id(kept))).findFirst().orElseThrow();
        assertThat(moved.get("x").decimalValue()).isEqualByComparingTo("0.9");
        assertThat(moved.get("rotation").asInt()).isEqualTo(90);
        assertThat(now.stream().map(p -> p.get("resourceId").asText()))
                .containsExactlyInAnyOrder(id(room1), id(room3));
    }

    @Test
    void positionErrorsNameTheirPositionAndAnActivePlanKeepsItsImage() throws Exception {
        JsonNode plan = activePlanWith(room1);
        long version = plan.get("version").asLong();

        JsonNode error = saveWorkingCopy(PROJECT_A, "floor-plans", id(plan), version, null,
                payload("Ground floor", id(image), List.of(newPosition(room2, 1.5, 0.2))), 422);
        assertThat(error.get("errors").get(0).get("path").asText()).isEqualTo("positions[0].x");

        JsonNode foreign = createPlace(PROJECT_B, "Elsewhere", "ROOM");
        JsonNode notFound = saveWorkingCopy(PROJECT_A, "floor-plans", id(plan), version, null,
                payload("Ground floor", id(image), List.of(newPosition(foreign, 0.5, 0.5))), 422);
        assertThat(notFound.get("errors").get(0).get("path").asText()).isEqualTo("positions[0].resourceId");

        JsonNode noImage = saveWorkingCopy(PROJECT_A, "floor-plans", id(plan), version, null,
                payload("Ground floor", null, List.of()), 422);
        assertThat(noImage.get("errors").get(0).get("path").asText()).isEqualTo("assetId");
    }

    @Test
    void theListFiltersPendingPlansAndArchivalDropsTheCopy() throws Exception {
        JsonNode pending = activePlanWith(room1);
        activePlanWith();
        saveWorkingCopy(PROJECT_A, "floor-plans", id(pending), pending.get("version").asLong(), null,
                payload("Ground floor (new)", id(image), List.of()), 200);

        mockMvc.perform(authenticated(get(plansUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id(pending)))
                .andExpect(jsonPath("$.content[0].hasWorkingCopy").value(true));

        changeStatus(plansUrl(PROJECT_A), id(pending), "ARCHIVED", pending.get("version").asLong(), 200);
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "floor-plans", id(pending)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void positionsOfAnActivePlanAreNotWrittenDirectly() throws Exception {
        JsonNode plan = activePlanWith();
        mockMvc.perform(json(post(plansUrl(PROJECT_A) + "/{id}/positions", id(plan)), Map.of("resourceType", "PLACE",
                        "resourceId", id(room1), "x", 0.5, "y", 0.5)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
        mockMvc.perform(json(put(plansUrl(PROJECT_A) + "/{id}", id(plan)), Map.of("version", plan.get("version").asLong(),
                        "internalName", "Renamed", "assetId", id(image))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    /** An ACTIVE plan with an image and the given Places positioned on it. */
    private JsonNode activePlanWith(JsonNode... places) throws Exception {
        JsonNode plan = read(mockMvc.perform(json(post(plansUrl(PROJECT_A)), Map.of("internalName", "Ground floor",
                        "assetId", id(image), "width", 2000, "height", 1400)))
                .andExpect(status().isCreated()).andReturn());
        for (JsonNode place : places) {
            mockMvc.perform(json(post(plansUrl(PROJECT_A) + "/{id}/positions", id(plan)), Map.of("resourceType", "PLACE",
                            "resourceId", id(place), "x", 0.5, "y", 0.5)))
                    .andExpect(status().isCreated());
        }
        return changeStatus(plansUrl(PROJECT_A), id(plan), "ACTIVE", plan.get("version").asLong(), 200);
    }

    private List<JsonNode> positions(JsonNode plan) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        read(mockMvc.perform(authenticated(get(plansUrl(PROJECT_A) + "/{id}/positions", id(plan)))).andReturn())
                .forEach(result::add);
        return result;
    }

    private Map<String, Object> payload(String name, String assetId, List<Map<String, Object>> positions) {
        Map<String, Object> payload = new HashMap<>(Map.of("internalName", name, "placeId", id(museum),
                "width", 2000, "height", 1400));
        payload.put("assetId", assetId);
        payload.put("positions", positions);
        return payload;
    }

    private static Map<String, Object> position(JsonNode existing, double x, double y, int rotation) {
        return Map.of("id", id(existing), "resourceType", existing.get("resourceType").asText(),
                "resourceId", existing.get("resourceId").asText(), "x", x, "y", y, "rotation", rotation);
    }

    private static Map<String, Object> newPosition(JsonNode place, double x, double y) {
        return Map.of("resourceType", "PLACE", "resourceId", id(place), "x", x, "y", y);
    }

    private static String plansUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/floor-plans";
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
