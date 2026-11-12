package eu.uniche.iguide.navigation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.InitializationFixtures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The visitor app's menus end to end against PostgreSQL ({@code 03} section 13, {@code 06} section 12, {@code 07}
 * section 14, {@code 08} Milestone 6; DECISIONS.md D-046).
 */
class NavigationIntegrationTest extends InitializationFixtures {

    private JsonNode kouros;
    private JsonNode harbour;

    @BeforeEach
    void setUp() throws Exception {
        initializeMuseum(PROJECT_A);
        languages(PROJECT_B);
        kouros = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        harbour = createPlace(PROJECT_A, "Harbour", "SITE");
    }

    @Test
    void aProjectHasOnePrimaryAndOneFooterNavigation() throws Exception {
        JsonNode primary = create(PROJECT_A, "PRIMARY", "Main menu", 201);
        assertThat(primary.get("context").asText()).isEqualTo("PRIMARY");
        assertThat(primary.get("items")).isEmpty();
        create(PROJECT_A, "FOOTER", "Footer links", 201);
        JsonNode twice = create(PROJECT_A, "PRIMARY", "Another", 409);
        assertThat(twice.get("errors").get(0).get("path").asText()).isEqualTo("context");
        assertThat(create(PROJECT_A, null, "", 422).get("errors")).hasSize(2);

        JsonNode list = read(mockMvc.perform(authenticated(get(url(PROJECT_A)))).andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(2);
        mockMvc.perform(json(put(url(PROJECT_A) + "/{id}", id(primary)),
                        Map.of("version", primary.get("version").asLong(), "name", "Visitor menu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Visitor menu"));
        mockMvc.perform(json(put(url(PROJECT_A) + "/{id}", id(primary)),
                        Map.of("version", primary.get("version").asLong(), "name", "Stale")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void itemsNestReorderMoveAndLeaveWithTheirSubtree() throws Exception {
        JsonNode menu = create(PROJECT_A, "PRIMARY", "Main menu", 201);
        JsonNode withVisit = addItem(menu, group("Επίσκεψη", "Visit"), 201);
        String visit = itemId(withVisit, 0);
        JsonNode withKouros = addItem(menu, link("POI", kouros, visit, Map.of("el", "Ο Κούρος")), 201);
        JsonNode withHarbour = addItem(menu, link("PLACE", harbour, visit, Map.of()), 201);
        JsonNode items = withHarbour.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(1).get("depth").asInt()).isEqualTo(1);
        assertThat(items.get(1).get("labels").get("el").asText()).isEqualTo("Ο Κούρος");
        assertThat(items.get(2).get("sortOrder").asInt()).isEqualTo(1);
        assertThat(withKouros.get("items").get(1).get("parentItemId").asText()).isEqualTo(visit);

        // Reorder in tree order: the harbour first inside "Visit".
        String kourosItem = items.get(1).get("id").asText();
        String harbourItem = items.get(2).get("id").asText();
        JsonNode reordered = read(mockMvc.perform(json(put(itemsUrl(menu) + ":order"),
                        Map.of("ids", List.of(visit, harbourItem, kourosItem))))
                .andExpect(status().isOk()).andReturn());
        assertThat(reordered.get("items").get(1).get("id").asText()).isEqualTo(harbourItem);
        mockMvc.perform(json(put(itemsUrl(menu) + ":order"), Map.of("ids", List.of(visit, harbourItem))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INCOMPLETE_ORDER"));

        // Move the kouros link to the top level; it goes last there and its old siblings close the gap.
        JsonNode kourosNow = item(reordered, kourosItem);
        JsonNode moved = updateItem(menu, kourosNow, link("POI", kouros, null, Map.of("en", "The Kouros")), 200);
        JsonNode top = item(moved, kourosItem);
        assertThat(top.get("depth").asInt()).isZero();
        assertThat(top.get("sortOrder").asInt()).isEqualTo(1);
        assertThat(top.get("labels").get("el").asText()).as("languages not sent keep their label").isEqualTo("Ο Κούρος");
        assertThat(item(moved, harbourItem).get("sortOrder").asInt()).isZero();

        // An item cannot sit under itself or its own sub-items.
        JsonNode visitNow = item(moved, visit);
        JsonNode cycle = updateItem(menu, visitNow, Map.of("itemType", "GROUP", "parentItemId", harbourItem), 422);
        assertThat(cycle.get("errors").get(0).get("code").asText()).isEqualTo("CYCLIC_PARENT");

        // A stale item version is refused; removing the group removes the harbour link under it.
        updateItem(menu, kourosNow, link("POI", kouros, null, Map.of()), 409);
        JsonNode removed = read(mockMvc.perform(authenticated(delete(itemsUrl(menu) + "/{i}", visit)
                        .param("version", visitNow.get("version").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(removed.get("items")).hasSize(1);
        assertThat(removed.get("items").get(0).get("id").asText()).isEqualTo(kourosItem);
        assertThat(removed.get("items").get(0).get("sortOrder").asInt()).isZero();
    }

    @Test
    void itemsOpenLiveResourcesOfTheProjectAndFooterLinksAreFlat() throws Exception {
        JsonNode menu = create(PROJECT_A, "PRIMARY", "Main menu", 201);
        JsonNode footer = create(PROJECT_A, "FOOTER", "Footer links", 201);
        JsonNode elsewhere = createPoi(PROJECT_B, "{\"title\": \"Elsewhere\"}");
        assertThat(code(addItem(menu, link("POI", elsewhere, null, Map.of()), 422))).isEqualTo("RESOURCE_NOT_FOUND");
        JsonNode gone = createPlace(PROJECT_A, "Gone", "SITE");
        changeStatus(placeUrl(PROJECT_A), id(gone), "ARCHIVED", gone.get("version").asLong(), 200);
        assertThat(code(addItem(menu, link("PLACE", gone, null, Map.of()), 422))).isEqualTo("ARCHIVED_REFERENCE");
        assertThat(code(addItem(menu, Map.of("itemType", "PAGE"), 422))).isEqualTo("UNSUPPORTED_TYPE");
        JsonNode unlabelled = addItem(menu, Map.of("itemType", "GROUP", "labels", Map.of("en", "Visit")), 422);
        assertThat(unlabelled.get("errors").get(0).get("path").asText()).isEqualTo("labels.el");
        assertThat(code(addItem(menu, link("POI", kouros, null, Map.of("fr", "Kouros")), 422))).isEqualTo("UNKNOWN_LANGUAGE");

        assertThat(code(addItem(footer, group("Πληροφορίες", "About"), 422))).isEqualTo("NOT_ALLOWED");
        JsonNode first = addItem(footer, link("PLACE", harbour, null, Map.of()), 201);
        JsonNode nested = addItem(footer, link("POI", kouros, itemId(first, 0), Map.of()), 422);
        assertThat(nested.get("errors").get(0).get("path").asText()).isEqualTo("parentItemId");

        // A menu of another Project, or another menu's item, is not reachable.
        mockMvc.perform(authenticated(get(url(PROJECT_B) + "/{id}", id(menu)))).andExpect(status().isNotFound());
        mockMvc.perform(json(put(itemsUrl(menu) + "/{i}", itemId(first, 0)), link("PLACE", harbour, null, Map.of())))
                .andExpect(status().isNotFound());
    }

    @Test
    void labelsAreSavedOneLanguageAtATime() throws Exception {
        JsonNode menu = create(PROJECT_A, "PRIMARY", "Main menu", 201);
        JsonNode added = addItem(menu, group("Επίσκεψη", null), 201);
        JsonNode visit = added.get("items").get(0);
        String labelUrl = itemsUrl(menu) + "/" + id(visit) + "/translations/";
        JsonNode english = read(mockMvc.perform(json(put(labelUrl + "en"),
                        Map.of("version", visit.get("version").asLong(), "label", "Visit")))
                .andExpect(status().isOk()).andReturn());
        assertThat(english.get("items").get(0).get("labels").get("en").asText()).isEqualTo("Visit");
        mockMvc.perform(authenticated(delete(labelUrl + "el").param("version", visit.get("version").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("label"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        mockMvc.perform(json(put(labelUrl + "fr"), Map.of("version", visit.get("version").asLong(), "label", "Visite")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("language"));
        JsonNode cleared = read(mockMvc.perform(authenticated(delete(labelUrl + "en")
                        .param("version", visit.get("version").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(cleared.get("items").get(0).get("labels").has("en")).isFalse();
    }

    @Test
    void everyWriteRaisesTheContentRevision() throws Exception {
        long start = revision(PROJECT_A);
        JsonNode menu = create(PROJECT_A, "PRIMARY", "Main menu", 201);
        JsonNode added = addItem(menu, link("POI", kouros, null, Map.of()), 201);
        updateItem(menu, added.get("items").get(0), link("POI", kouros, null, Map.of("en", "Kouros")), 200);
        mockMvc.perform(json(put(itemsUrl(menu) + ":order"), Map.of("ids", List.of(itemId(added, 0)))))
                .andExpect(status().isOk());
        assertThat(revision(PROJECT_A)).isEqualTo(start + 4);
        addItem(menu, Map.of("itemType", "PAGE"), 422);
        assertThat(revision(PROJECT_A)).as("a refused write").isEqualTo(start + 4);
    }

    @Test
    void aLinkedResourceListsTheMenuAndCannotLeaveVisitors() throws Exception {
        JsonNode active = changeStatus(poiUrl(PROJECT_A), id(kouros), "ACTIVE", kouros.get("version").asLong(), 200);
        JsonNode menu = create(PROJECT_A, "PRIMARY", "Main menu", 201);
        addItem(menu, link("POI", active, null, Map.of("el", "Ο Κούρος")), 201);
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}/references", id(active))))
                .andExpect(jsonPath("$[0].resourceType").value("NAVIGATION"))
                .andExpect(jsonPath("$[0].resourceId").value(id(menu)))
                .andExpect(jsonPath("$[0].title").value("Main menu: Ο Κούρος"));
        JsonNode refused = changeStatus(poiUrl(PROJECT_A), id(active), "ARCHIVED", active.get("version").asLong(), 409);
        assertThat(refused.get("code").asText()).isEqualTo("RESOURCE_IN_USE");
    }

    @Test
    void readinessReportsHeadingsWithoutLabelsAndTargetsWhoseCapabilityIsOff() throws Exception {
        JsonNode menu = create(PROJECT_A, "PRIMARY", "Main menu", 201);
        addItem(menu, group("Επίσκεψη", null), 201);
        JsonNode event = read(mockMvc.perform(json(post("/api/v1/projects/{p}/events", PROJECT_A),
                        Map.of("title", "Curator talk"))).andExpect(status().isCreated()).andReturn());
        addItem(menu, Map.of("itemType", "RESOURCE", "targetResourceType", "EVENT", "targetResourceId", id(event)), 201);
        JsonNode settings = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/settings", PROJECT_A)))
                .andReturn());
        mockMvc.perform(json(put("/api/v1/projects/{p}/settings", PROJECT_A), Map.of(
                        "featureConfiguration", Map.of("EVENTS", false), "version", settings.get("version").asLong())))
                .andExpect(status().isOk());

        JsonNode readiness = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/readiness", PROJECT_A)))
                .andExpect(status().isOk()).andReturn());
        List<String> warnings = new ArrayList<>();
        readiness.get("warnings").forEach(w -> {
            if (w.get("resourceType").asText().equals("NAVIGATION")) {
                warnings.add(w.get("path").asText() + " " + w.get("code").asText());
            }
        });
        assertThat(warnings).containsExactlyInAnyOrder("items[0].labels.en MISSING_TRANSLATION",
                "items[1].targetResourceType CAPABILITY_DISABLED");
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode create(UUID projectId, String context, String name, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("context", context);
        body.put("name", name);
        return read(mockMvc.perform(json(post(url(projectId)), body)).andExpect(status().is(expected)).andReturn());
    }

    private JsonNode addItem(JsonNode menu, Map<String, Object> body, int expected) throws Exception {
        return read(mockMvc.perform(json(post(itemsUrl(menu)), body)).andExpect(status().is(expected)).andReturn());
    }

    private JsonNode updateItem(JsonNode menu, JsonNode item, Map<String, Object> body, int expected) throws Exception {
        Map<String, Object> withVersion = new HashMap<>(body);
        withVersion.put("version", item.get("version").asLong());
        return read(mockMvc.perform(json(put(itemsUrl(menu) + "/{i}", id(item)), withVersion))
                .andExpect(status().is(expected)).andReturn());
    }

    private static Map<String, Object> link(String type, JsonNode target, String parent, Map<String, String> labels) {
        Map<String, Object> body = new HashMap<>(Map.of("itemType", "RESOURCE", "targetResourceType", type,
                "targetResourceId", id(target), "labels", labels));
        body.put("parentItemId", parent);
        return body;
    }

    private static Map<String, Object> group(String greek, String english) {
        Map<String, String> labels = new HashMap<>(Map.of("el", greek));
        if (english != null) {
            labels.put("en", english);
        }
        return Map.of("itemType", "GROUP", "labels", labels);
    }

    private static JsonNode item(JsonNode menu, String itemId) {
        for (JsonNode item : menu.get("items")) {
            if (item.get("id").asText().equals(itemId)) {
                return item;
            }
        }
        throw new AssertionError("No item " + itemId);
    }

    private static String itemId(JsonNode menu, int index) {
        return menu.get("items").get(index).get("id").asText();
    }

    private static String code(JsonNode problem) {
        return problem.get("errors").get(0).get("code").asText();
    }

    private long revision(UUID projectId) throws Exception {
        return read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/settings", projectId)))
                .andExpect(status().isOk()).andReturn()).get("contentRevision").asLong();
    }

    private static String url(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/navigations";
    }

    private static String itemsUrl(JsonNode menu) {
        return url(UUID.fromString(menu.get("projectId").asText())) + "/" + id(menu) + "/items";
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
