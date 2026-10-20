package eu.uniche.iguide.floorplan.api;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Floor Plans and their positions ({@code 03} section 8, {@code 08} Milestone 5). */
class FloorPlanIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    @Test
    void anIndoorPlaceHasSeveralPlansInFloorOrder() throws Exception {
        JsonNode museum = createPlace(PROJECT_A, "Museum", "BUILDING");
        JsonNode ground = createPlan(PROJECT_A, Map.of("internalName", "Ground floor", "placeId", museum.get("id").asText()));
        JsonNode first = createPlan(PROJECT_A, Map.of("internalName", "First floor", "placeId", museum.get("id").asText()));

        mockMvc.perform(authenticated(get(plansUrl(PROJECT_A)).param("placeId", museum.get("id").asText())))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(ground.get("id").asText()));

        mockMvc.perform(json(put(plansUrl(PROJECT_A) + ":order"),
                        Map.of("ids", List.of(first.get("id").asText(), ground.get("id").asText()))))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(plansUrl(PROJECT_A)).param("placeId", museum.get("id").asText())))
                .andExpect(jsonPath("$.content[0].id").value(first.get("id").asText()));
    }

    @Test
    void aPlanLinksOnlyAnIndoorPlace() throws Exception {
        JsonNode site = createPlace(PROJECT_A, "Acropolis", "SITE");
        mockMvc.perform(json(post(plansUrl(PROJECT_A)), Map.of("internalName", "Plan", "placeId", site.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("ENVIRONMENT_MISMATCH"));
    }

    @Test
    void activationNeedsAnImage() throws Exception {
        JsonNode plan = createPlan(PROJECT_A, Map.of("internalName", "Ground floor"));
        JsonNode error = changeStatus(plansUrl(PROJECT_A), plan.get("id").asText(), "ACTIVE", plan.get("version").asLong(), 422);
        assertThat(error.get("errors").get(0).get("path").asText()).isEqualTo("assetId");

        Map<String, Object> missing = new HashMap<>(Map.of("version", plan.get("version").asLong(),
                "internalName", "Ground floor", "assetId", UUID.randomUUID().toString()));
        mockMvc.perform(json(put(plansUrl(PROJECT_A) + "/{id}", plan.get("id").asText()), missing))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));

        JsonNode image = uploadAsset(PROJECT_A, "ground.png", "image/png", png(20, 14), "type", "FLOOR_PLAN");
        Map<String, Object> update = new HashMap<>(Map.of("version", plan.get("version").asLong(),
                "internalName", "Ground floor", "assetId", image.get("id").asText(), "width", 2000, "height", 1400));
        JsonNode withImage = read(mockMvc.perform(json(put(plansUrl(PROJECT_A) + "/{id}", plan.get("id").asText()), update))
                .andExpect(status().isOk()).andReturn());
        changeStatus(plansUrl(PROJECT_A), plan.get("id").asText(), "ACTIVE", withImage.get("version").asLong(), 200);
    }

    @Test
    void positionsAreNormalizedAndAPositionedPlaceIsInUse() throws Exception {
        JsonNode plan = createPlan(PROJECT_A, Map.of("internalName", "Ground floor"));
        JsonNode room = createPlace(PROJECT_A, "Room 1", "ROOM");
        String positions = plansUrl(PROJECT_A) + "/" + plan.get("id").asText() + "/positions";

        mockMvc.perform(json(post(positions), Map.of("resourceType", "PLACE", "resourceId", room.get("id").asText(),
                        "x", 1.2, "y", 0.5, "rotation", 360)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.path == 'x')].code").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.errors[?(@.path == 'rotation')].code").value("OUT_OF_RANGE"));

        mockMvc.perform(json(post(positions), Map.of("resourceType", "PLACE", "resourceId", room.get("id").asText(),
                        "x", 0.25, "y", 0.75, "rotation", 90)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.x").value(0.25));

        JsonNode inUse = changeStatus(placeUrl(PROJECT_A), room.get("id").asText(), "ARCHIVED",
                room.get("version").asLong(), 409);
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("FLOOR_PLAN");
    }

    @Test
    void anotherProjectsPlanAndPlaceAreInvisible() throws Exception {
        JsonNode foreignPlan = createPlan(PROJECT_B, Map.of("internalName", "Elsewhere"));
        JsonNode foreignRoom = createPlace(PROJECT_B, "Room", "ROOM");
        JsonNode ownPlan = createPlan(PROJECT_A, Map.of("internalName", "Ground floor"));

        mockMvc.perform(authenticated(get(plansUrl(PROJECT_A) + "/{id}", foreignPlan.get("id").asText())))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post(plansUrl(PROJECT_A)), Map.of("internalName", "Plan", "placeId",
                        foreignRoom.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(json(post(plansUrl(PROJECT_A) + "/{id}/positions", ownPlan.get("id").asText()),
                        Map.of("resourceType", "PLACE", "resourceId", foreignRoom.get("id").asText(), "x", 0.5, "y", 0.5)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(json(put(plansUrl(PROJECT_A) + ":order"),
                        Map.of("ids", List.of(ownPlan.get("id").asText(), foreignPlan.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity());
    }

    private JsonNode createPlan(UUID projectId, Map<String, Object> body) throws Exception {
        return read(mockMvc.perform(json(post(plansUrl(projectId)), body)).andExpect(status().isCreated()).andReturn());
    }

    private static String plansUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/floor-plans";
    }
}
