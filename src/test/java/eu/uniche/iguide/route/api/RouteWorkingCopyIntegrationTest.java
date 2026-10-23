package eu.uniche.iguide.route.api;

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

/** The working copy of an ACTIVE Route: fields, translations, stops, Category, and media (DECISIONS.md D-027). */
class RouteWorkingCopyIntegrationTest extends ContentIntegrationTest {

    private JsonNode kouros;
    private JsonNode kore;
    private JsonNode amphora;

    @BeforeEach
    void setUp() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
        kouros = activePoi(PROJECT_A, "Kouros");
        kore = activePoi(PROJECT_A, "Kore");
        amphora = activePoi(PROJECT_A, "Amphora");
    }

    @Test
    void aSaveLeavesTheLiveRouteAndItsStopsUnchanged() throws Exception {
        JsonNode route = activeRoute(kouros, kore);
        Map<String, Object> state = state(route);
        state.put("travelMode", "BIKING");
        state.put("stops", List.of(stop(route, 1, kore, Map.of()), newStop(amphora)));

        JsonNode copy = saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(), null, state, 200);
        assertThat(copy.get("resource").get("travelMode").asText()).isEqualTo("BIKING");
        assertThat(poiIds(copy.get("resource"))).containsExactly(id(kore), id(amphora));

        JsonNode live = reload(route);
        assertThat(live.get("travelMode").asText()).isEqualTo(route.get("travelMode").asText());
        assertThat(poiIds(live)).containsExactly(id(kouros), id(kore));
        assertThat(live.get("version").asLong()).isEqualTo(route.get("version").asLong());
        assertThat(live.get("hasWorkingCopy").asBoolean()).isTrue();
        assertThat(stopIds(live)).isEqualTo(stopIds(route));
    }

    @Test
    void applyRewritesFieldsTranslationsStopsCategoryAndMediaAndDeletesTheCopy() throws Exception {
        JsonNode route = activeRoute(kouros, kore);
        JsonNode category = read(mockMvc.perform(json(post("/api/v1/projects/{p}/categories", PROJECT_A),
                Map.of("name", "Trails", "scope", "ROUTE"))).andExpect(status().isCreated()).andReturn());
        JsonNode image = uploadAsset(PROJECT_A, "map.png", "image/png", png(8, 8));

        Map<String, Object> state = state(route);
        state.put("travelMode", "BIKING");
        state.put("loop", true);
        state.put("translations", Map.of("el", Map.of("title", "Νέα διαδρομή"), "en", Map.of("title", "New route")));
        // Reordered, the first stop removed, the second kept with new texts, and a new one added first.
        state.put("stops", List.of(newStop(amphora), stop(route, 1, kore, Map.of("el", Map.of("narrative", "Εδώ")))));
        state.put("categoryIds", List.of(id(category)));
        state.put("media", List.of(Map.of("assetId", id(image), "usage", "HERO_IMAGE")));
        saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(), null, state, 200);

        JsonNode applied = applyWorkingCopy(PROJECT_A, "routes", id(route), 200);
        assertThat(applied.get("travelMode").asText()).isEqualTo("BIKING");
        assertThat(applied.get("loop").asBoolean()).isTrue();
        assertThat(applied.get("hasWorkingCopy").asBoolean()).isFalse();

        JsonNode live = reload(route);
        assertThat(live.get("translations").get("el").get("title").asText()).isEqualTo("Νέα διαδρομή");
        assertThat(live.get("translations").get("en").get("title").asText()).isEqualTo("New route");
        assertThat(poiIds(live)).containsExactly(id(amphora), id(kore));
        assertThat(live.get("stops").get(0).get("position").asInt()).isZero();
        assertThat(live.get("stops").get(1).get("position").asInt()).isEqualTo(1);
        assertThat(live.get("stops").get(1).get("id").asText()).isEqualTo(stopIds(route).get(1));
        assertThat(live.get("stops").get(1).get("translations").get("el").get("narrative").asText()).isEqualTo("Εδώ");
        assertThat(live.get("categoryIds")).extracting(JsonNode::asText).containsExactly(id(category));
        assertThat(live.get("media")).hasSize(1);
        assertThat(live.get("media").get(0).get("usage").asText()).isEqualTo("HERO_IMAGE");
        assertThat(live.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "routes", id(route)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStopWhosePoiChangesKeepsItsTexts() throws Exception {
        JsonNode route = activeRoute(kouros, kore);
        JsonNode texted = reload(route);
        Map<String, Object> state = state(texted);
        state.put("stops", List.of(stop(texted, 0, amphora, Map.of("en", Map.of("narrative", "Here"))),
                stop(texted, 1, kore, Map.of())));
        saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(), null, state, 200);
        applyWorkingCopy(PROJECT_A, "routes", id(route), 200);

        JsonNode live = reload(route);
        assertThat(poiIds(live)).containsExactly(id(amphora), id(kore));
        assertThat(live.get("stops").get(0).get("translations").get("el").get("narrative").asText())
                .isEqualTo("Ξεκινάμε εδώ");
        assertThat(live.get("stops").get(0).get("translations").get("en").get("narrative").asText()).isEqualTo("Here");
    }

    @Test
    void aStopErrorIsReportedAtItsPositionInThePayload() throws Exception {
        JsonNode route = activeRoute(kouros, kore);
        JsonNode foreign = createPoi(PROJECT_B, "{\"title\": \"Elsewhere\"}");

        Map<String, Object> state = state(route);
        state.put("stops", List.of(stop(route, 0, kouros, Map.of()), newStop(foreign)));
        JsonNode unknown = saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(), null, state, 422);
        assertThat(unknown.get("errors").get(0).get("path").asText()).isEqualTo("stops[1].poiId");
        assertThat(unknown.get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");

        state.put("stops", List.of(stop(route, 0, kouros, Map.of()), newStop(kouros)));
        JsonNode duplicate = saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(), null, state, 422);
        assertThat(duplicate.get("errors").get(0).get("path").asText()).isEqualTo("stops[1].poiId");
        assertThat(duplicate.get("errors").get(0).get("code").asText()).isEqualTo("DUPLICATE_STOP");

        JsonNode live = reload(route);
        assertThat(live.get("hasWorkingCopy").asBoolean()).isFalse();
        assertThat(poiIds(live)).containsExactly(id(kouros), id(kore));
    }

    @Test
    void pendingRoutesAreListedAndDeactivationDropsTheCopy() throws Exception {
        JsonNode route = activeRoute(kouros, kore);
        activeRoute(amphora);
        saveWorkingCopy(PROJECT_A, "routes", id(route), route.get("version").asLong(), null, state(route), 200);

        mockMvc.perform(authenticated(get(routesUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id(route)))
                .andExpect(jsonPath("$.content[0].hasWorkingCopy").value(true));

        JsonNode draft = changeStatus(routesUrl(PROJECT_A), id(route), "DRAFT", route.get("version").asLong(), 200);
        assertThat(draft.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "routes", id(route)))))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get(routesUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode activeRoute(JsonNode... pois) throws Exception {
        JsonNode route = read(mockMvc.perform(json(post(routesUrl(PROJECT_A)),
                Map.of("title", "Highlights " + UUID.randomUUID(), "type", "INDOOR")))
                .andExpect(status().isCreated()).andReturn());
        boolean first = true;
        for (JsonNode poi : pois) {
            Map<String, Object> texts = first ? Map.of("el", Map.of("narrative", "Ξεκινάμε εδώ")) : Map.of();
            mockMvc.perform(json(post(routesUrl(PROJECT_A) + "/{id}/stops", id(route)),
                    Map.of("poiId", id(poi), "translations", texts))).andExpect(status().isCreated());
            first = false;
        }
        JsonNode loaded = reload(route);
        return changeStatus(routesUrl(PROJECT_A), id(route), "ACTIVE", loaded.get("version").asLong(), 200);
    }

    private JsonNode activePoi(UUID projectId, String title) throws Exception {
        JsonNode poi = createPoi(projectId, "{\"title\": \"" + title + "\"}");
        return changeStatus(poiUrl(projectId), id(poi), "ACTIVE", poi.get("version").asLong(), 200);
    }

    private JsonNode reload(JsonNode route) throws Exception {
        return read(mockMvc.perform(authenticated(get(routesUrl(PROJECT_A) + "/{id}", id(route))))
                .andExpect(status().isOk()).andReturn());
    }

    /** The Route's current editable state, as the CMS would send it. */
    private Map<String, Object> state(JsonNode route) {
        Map<String, Object> state = new HashMap<>();
        state.put("slug", route.get("slug").asText());
        state.put("type", route.get("type").asText());
        state.put("travelMode", route.get("travelMode").isNull() ? null : route.get("travelMode").asText());
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

    private static Map<String, Object> stop(JsonNode route, int index, JsonNode poi, Map<String, Object> translations) {
        return Map.of("id", route.get("stops").get(index).get("id").asText(), "poiId", id(poi),
                "translations", translations);
    }

    private static Map<String, Object> newStop(JsonNode poi) {
        return Map.of("poiId", id(poi));
    }

    private static List<String> poiIds(JsonNode route) {
        List<String> ids = new ArrayList<>();
        route.get("stops").forEach(s -> ids.add(s.get("poiId").asText()));
        return ids;
    }

    private static List<String> stopIds(JsonNode route) {
        List<String> ids = new ArrayList<>();
        route.get("stops").forEach(s -> ids.add(s.get("id").asText()));
        return ids;
    }

    private static String routesUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/routes";
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
