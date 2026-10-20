package eu.uniche.iguide.route.api;

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

/** Routes and their stops end to end against PostgreSQL ({@code 03} section 9, {@code 08} Milestone 5). */
class RouteIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    // --- Create and update -----------------------------------------------------------------------

    @Test
    void createsADraftWithItsTypeTitleAndAGeneratedSlug() throws Exception {
        JsonNode route = createRoute(PROJECT_A, "Highlights in 45 minutes", "INDOOR");
        assertThat(route.get("status").asText()).isEqualTo("DRAFT");
        assertThat(route.get("type").asText()).isEqualTo("INDOOR");
        assertThat(route.get("slug").asText()).isEqualTo("highlights-in-45-minutes");
        assertThat(route.get("stops")).isEmpty();
        assertThat(route.get("translations").get("el").get("title").asText()).isEqualTo("Highlights in 45 minutes");

        mockMvc.perform(json(post(routesUrl(PROJECT_A)), "{\"title\": \"No type\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("type"));
        mockMvc.perform(json(post(routesUrl(PROJECT_A)),
                        "{\"title\": \"Again\", \"type\": \"INDOOR\", \"slug\": \"highlights-in-45-minutes\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("DUPLICATE_VALUE"));
    }

    @Test
    void savesADraftsFieldsAndRejectsOutOfRangeValuesAndAStaleVersion() throws Exception {
        JsonNode route = createRoute(PROJECT_A, "Trail", "OUTDOOR");
        JsonNode saved = update(route, Map.of("travelMode", "HIKING", "difficulty", "HARD",
                "estimatedDurationMinutes", 90, "distanceMeters", 4200, "loop", true,
                "translations", Map.of("en", Map.of("title", "Trail", "instructions", "Wear boots"))), 200);
        assertThat(saved.get("travelMode").asText()).isEqualTo("HIKING");
        assertThat(saved.get("difficulty").asText()).isEqualTo("HARD");
        assertThat(saved.get("distanceMeters").asInt()).isEqualTo(4200);
        assertThat(saved.get("loop").asBoolean()).isTrue();
        assertThat(saved.get("translations").get("en").get("instructions").asText()).isEqualTo("Wear boots");

        JsonNode invalid = update(saved, Map.of("estimatedDurationMinutes", 0, "distanceMeters", 2_000_000), 422);
        assertThat(invalid.get("errors")).extracting(e -> e.get("path").asText())
                .contains("estimatedDurationMinutes", "distanceMeters");
        JsonNode conflict = update(route, Map.of(), 409);
        assertThat(conflict.get("type").asText()).isEqualTo("VERSION_CONFLICT");
    }

    // --- Stops -----------------------------------------------------------------------------------

    @Test
    void addsUpdatesReordersAndRemovesStopsWithTheirOwnTexts() throws Exception {
        JsonNode route = createRoute(PROJECT_A, "Highlights", "INDOOR");
        JsonNode kouros = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        JsonNode kore = createPoi(PROJECT_A, "{\"title\": \"Kore\"}");
        JsonNode amphora = createPoi(PROJECT_A, "{\"title\": \"Amphora\"}");

        JsonNode first = addStop(route, kouros, Map.of("el", Map.of("narrative", "Ξεκινάμε εδώ")), 201);
        JsonNode second = addStop(route, kore, Map.of(), 201);
        JsonNode third = addStop(route, amphora, Map.of(), 201);
        assertThat(List.of(first.get("position").asInt(), second.get("position").asInt(), third.get("position").asInt()))
                .containsExactly(0, 1, 2);

        mockMvc.perform(json(put(stopsUrl(route) + "/{s}", second.get("id").asText()), Map.of(
                        "poiId", kore.get("id").asText(),
                        "translations", Map.of("en", Map.of("titleOverride", "The Kore", "navigationInstructions", "Turn left")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.en.titleOverride").value("The Kore"));

        mockMvc.perform(json(put(stopsUrl(route) + ":order"), Map.of("ids", List.of(
                        third.get("id").asText(), first.get("id").asText(), second.get("id").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(third.get("id").asText()));

        mockMvc.perform(authenticated(delete(stopsUrl(route) + "/{s}", first.get("id").asText())))
                .andExpect(status().isNoContent());
        JsonNode reloaded = read(mockMvc.perform(authenticated(get(routesUrl(PROJECT_A) + "/{id}", route.get("id").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(reloaded.get("stops")).extracting(s -> s.get("id").asText())
                .containsExactly(third.get("id").asText(), second.get("id").asText());
        assertThat(reloaded.get("stops")).extracting(s -> s.get("position").asInt()).containsExactly(0, 1);

        // The stop's narrative never changes the Point of Interest's own text.
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", kouros.get("id").asText())))
                .andExpect(jsonPath("$.translations.el.title").value("Kouros"))
                .andExpect(jsonPath("$.translations.el.description").doesNotExist());
    }

    @Test
    void aStopNeedsAnUnusedLiveSameProjectPoi() throws Exception {
        JsonNode route = createRoute(PROJECT_A, "Highlights", "INDOOR");
        JsonNode kouros = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        addStop(route, kouros, Map.of(), 201);
        assertThat(addStop(route, kouros, Map.of(), 422).get("errors").get(0).get("code").asText()).isEqualTo("DUPLICATE_STOP");

        JsonNode archived = createPoi(PROJECT_A, "{\"title\": \"Gone\"}");
        changeStatus(poiUrl(PROJECT_A), archived.get("id").asText(), "ARCHIVED", archived.get("version").asLong(), 200);
        assertThat(addStop(route, archived, Map.of(), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("ARCHIVED_REFERENCE");

        JsonNode foreign = createPoi(PROJECT_B, "{\"title\": \"Elsewhere\"}");
        assertThat(addStop(route, foreign, Map.of(), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void anActiveRouteAndItsStopsAreNotEditedInPlace() throws Exception {
        JsonNode route = createRoute(PROJECT_A, "Highlights", "INDOOR");
        JsonNode kouros = activePoi("Kouros");
        addStop(route, kouros, Map.of(), 201);
        JsonNode active = changeStatus(routesUrl(PROJECT_A), route.get("id").asText(), "ACTIVE",
                reload(route).get("version").asLong(), 200);
        assertThat(update(active, Map.of(), 422).get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_EDITABLE");
        assertThat(addStop(active, createPoi(PROJECT_A, "{\"title\": \"Kore\"}"), Map.of(), 422)
                .get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_EDITABLE");
    }

    // --- Lifecycle and references ----------------------------------------------------------------

    @Test
    void activationNeedsTheTitleAndAStopWhosePoiIsActive() throws Exception {
        JsonNode route = createRoute(PROJECT_A, "Highlights", "INDOOR");
        JsonNode draftPoi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        addStop(route, draftPoi, Map.of(), 201);
        JsonNode missing = changeStatus(routesUrl(PROJECT_A), route.get("id").asText(), "ACTIVE",
                reload(route).get("version").asLong(), 422);
        assertThat(missing.get("errors").get(0).get("path").asText()).isEqualTo("stops");

        changeStatus(poiUrl(PROJECT_A), draftPoi.get("id").asText(), "ACTIVE", draftPoi.get("version").asLong(), 200);
        changeStatus(routesUrl(PROJECT_A), route.get("id").asText(), "ACTIVE", reload(route).get("version").asLong(), 200);
    }

    @Test
    void aPoiOnANonArchivedRouteIsInUse() throws Exception {
        JsonNode route = createRoute(PROJECT_A, "Highlights", "INDOOR");
        JsonNode kouros = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        addStop(route, kouros, Map.of(), 201);

        JsonNode inUse = changeStatus(poiUrl(PROJECT_A), kouros.get("id").asText(), "ARCHIVED", kouros.get("version").asLong(), 409);
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("ROUTE");
        assertThat(inUse.get("references").get(0).get("title").asText()).isEqualTo("Highlights");

        changeStatus(routesUrl(PROJECT_A), route.get("id").asText(), "ARCHIVED", reload(route).get("version").asLong(), 200);
        changeStatus(poiUrl(PROJECT_A), kouros.get("id").asText(), "ARCHIVED", kouros.get("version").asLong(), 200);
    }

    // --- Distance --------------------------------------------------------------------------------

    @Test
    void computesTheDistanceThroughTheStopsAndClosesALoop() throws Exception {
        JsonNode route = createRoute(PROJECT_A, "Trail", "OUTDOOR");
        // Three points 0.01 degrees of latitude apart (about 1112 m each) and one back along longitude.
        addStop(route, poiAt("A", 38.00, 23.70), Map.of(), 201);
        addStop(route, poiAt("B", 38.01, 23.70), Map.of(), 201);
        addStop(route, poiAt("C", 38.02, 23.70), Map.of(), 201);
        addStop(route, createPoi(PROJECT_A, "{\"title\": \"No coordinates\"}"), Map.of(), 201);

        JsonNode open = reload(route);
        assertThat(open.get("computedDistanceMeters").asInt()).isBetween(2220, 2226);
        assertThat(open.get("distanceMeters").isNull()).isTrue();

        JsonNode loop = update(open, Map.of("loop", true), 200);
        assertThat(loop.get("computedDistanceMeters").asInt()).isBetween(4440, 4452);
    }

    // --- Listing and order -----------------------------------------------------------------------

    @Test
    void listsInOrderSearchesTitlesAndReorders() throws Exception {
        JsonNode first = createRoute(PROJECT_A, "Highlights", "INDOOR");
        JsonNode second = createRoute(PROJECT_A, "Family trail", "OUTDOOR");
        mockMvc.perform(authenticated(get(routesUrl(PROJECT_A))))
                .andExpect(jsonPath("$.content[0].id").value(first.get("id").asText()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(authenticated(get(routesUrl(PROJECT_A)).param("q", "FAMILY")))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(json(put(routesUrl(PROJECT_A) + ":order"),
                        Map.of("ids", List.of(second.get("id").asText(), first.get("id").asText()))))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(routesUrl(PROJECT_A))))
                .andExpect(jsonPath("$.content[0].id").value(second.get("id").asText()));
    }

    // --- Cross-Project isolation -----------------------------------------------------------------

    @Test
    void anotherProjectsRouteAndStopsAreInvisible() throws Exception {
        JsonNode foreignRoute = createRoute(PROJECT_B, "Elsewhere", "INDOOR");
        JsonNode foreignStop = addStop(foreignRoute, createPoi(PROJECT_B, "{\"title\": \"Far\"}"), Map.of(), 201);
        JsonNode ownRoute = createRoute(PROJECT_A, "Highlights", "INDOOR");

        mockMvc.perform(authenticated(get(routesUrl(PROJECT_A) + "/{id}", foreignRoute.get("id").asText())))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(delete(stopsUrl(ownRoute) + "/{s}", foreignStop.get("id").asText())))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(put(stopsUrl(ownRoute) + ":order"), Map.of("ids", List.of(foreignStop.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode createRoute(UUID projectId, String title, String type) throws Exception {
        return read(mockMvc.perform(json(post(routesUrl(projectId)), Map.of("title", title, "type", type)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode addStop(JsonNode route, JsonNode poi, Map<String, Object> translations, int expected) throws Exception {
        return read(mockMvc.perform(json(post(stopsUrl(route)), Map.of("poiId", poi.get("id").asText(),
                        "translations", translations)))
                .andExpect(status().is(expected)).andReturn());
    }

    private JsonNode reload(JsonNode route) throws Exception {
        return read(mockMvc.perform(authenticated(get(routesUrl(UUID.fromString(route.get("projectId").asText()))
                        + "/{id}", route.get("id").asText())))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode update(JsonNode route, Map<String, Object> changes, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("version", route.get("version").asLong());
        body.put("slug", route.get("slug").asText());
        body.put("type", route.get("type").asText());
        body.put("loop", route.get("loop").asBoolean());
        body.put("featured", false);
        body.putAll(changes);
        return read(mockMvc.perform(json(put(routesUrl(PROJECT_A) + "/{id}", route.get("id").asText()), body))
                .andExpect(status().is(expected)).andReturn());
    }

    private JsonNode activePoi(String title) throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"" + title + "\"}");
        return changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(), 200);
    }

    private JsonNode poiAt(String title, double latitude, double longitude) throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"" + title + "\"}");
        Map<String, Object> body = new HashMap<>(Map.of("version", poi.get("version").asLong(), "slug",
                poi.get("slug").asText(), "latitude", latitude, "longitude", longitude, "featured", false));
        return read(mockMvc.perform(json(put(poiUrl(PROJECT_A) + "/{id}", poi.get("id").asText()), body))
                .andExpect(status().isOk()).andReturn());
    }

    private static String routesUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/routes";
    }

    private static String stopsUrl(JsonNode route) {
        return routesUrl(UUID.fromString(route.get("projectId").asText())) + "/" + route.get("id").asText() + "/stops";
    }
}
