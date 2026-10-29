package eu.uniche.iguide.workingcopy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * An apply that would add blocking readiness issues is refused and rolled back; issues the Project already had never
 * stop one ({@code 14} section 2, DECISIONS.md D-037).
 */
class ApplyReadinessIntegrationTest extends ContentIntegrationTest {

    private JsonNode kouros;
    private JsonNode kore;
    private JsonNode draftPoi;

    @BeforeEach
    void setUp() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
        kouros = activePoi("Kouros");
        kore = activePoi("Kore");
        draftPoi = createPoi(PROJECT_A, "{\"title\": \"Not yet\"}");
    }

    @Test
    void aRouteLeftWithoutAnActiveStopIsRefusedAndStaysAsItWas() throws Exception {
        JsonNode route = activeRoute(kouros);
        Map<String, Object> state = state(route);
        state.put("stops", List.of(Map.of("poiId", id(draftPoi))));
        saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(), null, state, 200);

        JsonNode refused = applyWorkingCopy(PROJECT_A, "routes", id(route), 422);
        assertThat(refused.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(refused.get("errors")).hasSize(1);
        assertThat(refused.get("errors").get(0).get("path").asText()).isEqualTo("stops");
        assertThat(refused.get("errors").get(0).get("code").asText()).isEqualTo("ROUTE_WITHOUT_ACTIVE_STOP");

        JsonNode live = reload(route);
        assertThat(poiIds(live)).containsExactly(id(kouros));
        assertThat(live.get("version").asLong()).isEqualTo(route.get("version").asLong());
        assertThat(live.get("hasWorkingCopy").asBoolean()).isTrue();
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "routes", id(route)))))
                .andExpect(status().isOk());

        // Fixed in the copy, the same apply goes through.
        JsonNode copy = read(mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "routes", id(route)))))
                .andExpect(status().isOk()).andReturn());
        state.put("stops", List.of(Map.of("poiId", id(draftPoi)), Map.of("poiId", id(kore))));
        saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(),
                copy.get("version").asLong(), state, 200);
        applyWorkingCopy(PROJECT_A, "routes", id(route), 200);
        assertThat(poiIds(reload(route))).containsExactly(id(draftPoi), id(kore));
    }

    @Test
    void aBlankDefaultLanguageTitleIsRefusedAtItsField() throws Exception {
        JsonNode route = activeRoute(kouros);
        Map<String, Object> state = state(route);
        state.put("translations", Map.of("el", Map.of("title", " "), "en", Map.of("title", "Highlights")));
        saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(), null, state, 200);

        JsonNode refused = applyWorkingCopy(PROJECT_A, "routes", id(route), 422);
        assertThat(refused.get("errors").get(0).get("path").asText()).isEqualTo("translations.el.title");
        assertThat(refused.get("errors").get(0).get("code").asText()).isEqualTo("MISSING_DEFAULT_LANGUAGE_VALUE");
        assertThat(reload(route).get("translations").get("el").get("title").asText())
                .isEqualTo(route.get("translations").get("el").get("title").asText());
    }

    @Test
    void issuesTheProjectAlreadyHasDoNotStopAnApply() throws Exception {
        JsonNode route = activeRoute(kouros);
        // No surface is enabled in this Project: that blocks readiness before the apply, and the apply leaves it so.
        JsonNode readiness = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/readiness", PROJECT_A)))
                .andExpect(status().isOk()).andReturn());
        List<String> codes = new ArrayList<>();
        readiness.get("blocking").forEach(issue -> codes.add(issue.get("code").asText()));
        assertThat(codes).contains("NO_SURFACE_ENABLED");

        Map<String, Object> state = state(route);
        state.put("travelMode", "BIKING");
        state.put("stops", List.of(Map.of("poiId", id(kore)), Map.of("poiId", id(kouros))));
        saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(), null, state, 200);

        JsonNode applied = applyWorkingCopy(PROJECT_A, "routes", id(route), 200);
        assertThat(applied.get("travelMode").asText()).isEqualTo("BIKING");
        assertThat(poiIds(reload(route))).containsExactly(id(kore), id(kouros));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode activeRoute(JsonNode... pois) throws Exception {
        JsonNode route = read(mockMvc.perform(json(post(routesUrl()),
                Map.of("title", "Highlights " + UUID.randomUUID(), "type", "INDOOR")))
                .andExpect(status().isCreated()).andReturn());
        for (JsonNode poi : pois) {
            mockMvc.perform(json(post(routesUrl() + "/{id}/stops", id(route)), Map.of("poiId", id(poi))))
                    .andExpect(status().isCreated());
        }
        return changeStatus(routesUrl(), id(route), "ACTIVE", reload(route).get("version").asLong(), 200);
    }

    private JsonNode activePoi(String title) throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"" + title + "\"}");
        return changeStatus(poiUrl(PROJECT_A), id(poi), "ACTIVE", poi.get("version").asLong(), 200);
    }

    private JsonNode reload(JsonNode route) throws Exception {
        return read(mockMvc.perform(authenticated(get(routesUrl() + "/{id}", id(route))))
                .andExpect(status().isOk()).andReturn());
    }

    /** The Route's current editable state, as the CMS would send it. */
    private Map<String, Object> state(JsonNode route) {
        Map<String, Object> state = new HashMap<>();
        state.put("slug", route.get("slug").asText());
        state.put("type", route.get("type").asText());
        state.put("loop", route.get("loop").asBoolean());
        state.put("featured", route.get("featured").asBoolean());
        state.put("translations", Map.of("el", Map.of("title", route.get("translations").get("el").get("title").asText())));
        List<Object> stops = new ArrayList<>();
        for (JsonNode stop : route.get("stops")) {
            stops.add(Map.of("id", stop.get("id").asText(), "poiId", stop.get("poiId").asText()));
        }
        state.put("stops", stops);
        return state;
    }

    private static List<String> poiIds(JsonNode route) {
        List<String> ids = new ArrayList<>();
        route.get("stops").forEach(s -> ids.add(s.get("poiId").asText()));
        return ids;
    }

    private static String routesUrl() {
        return "/api/v1/projects/" + PROJECT_A + "/routes";
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
