package eu.uniche.iguide.event.api;

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

/** Events and their occurrences end to end against PostgreSQL ({@code 03} section 15, {@code 08} Milestone 9). */
class EventIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    // --- Create and update -----------------------------------------------------------------------

    @Test
    void createsADraftAndSavesItsLinksTextsAndBookingUrl() throws Exception {
        JsonNode event = createEvent(PROJECT_A, "Curator talk");
        assertThat(event.get("status").asText()).isEqualTo("DRAFT");
        assertThat(event.get("slug").asText()).isEqualTo("curator-talk");
        assertThat(event.get("occurrences")).isEmpty();

        JsonNode place = createPlace(PROJECT_A, "Museum", "BUILDING");
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        JsonNode saved = update(event, Map.of("placeId", place.get("id").asText(), "poiId", poi.get("id").asText(),
                "bookingUrl", " https://tickets.example.org/talk ", "translations",
                Map.of("en", Map.of("title", "Curator talk", "locationText", "Ground floor"))), 200);
        assertThat(saved.get("placeId").asText()).isEqualTo(place.get("id").asText());
        assertThat(saved.get("bookingUrl").asText()).isEqualTo("https://tickets.example.org/talk");
        assertThat(saved.get("translations").get("en").get("locationText").asText()).isEqualTo("Ground floor");

        assertThat(update(saved, Map.of("bookingUrl", "ftp://example.org"), 422).get("errors").get(0).get("path").asText())
                .isEqualTo("bookingUrl");
        assertThat(update(event, Map.of(), 409).get("type").asText()).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void linksMustBeLiveAndInTheProject() throws Exception {
        JsonNode event = createEvent(PROJECT_A, "Curator talk");
        JsonNode archived = createPlace(PROJECT_A, "Old wing", "AREA");
        changeStatus(placeUrl(PROJECT_A), archived.get("id").asText(), "ARCHIVED", archived.get("version").asLong(), 200);
        JsonNode foreign = createPoi(PROJECT_B, "{\"title\": \"Far\"}");
        JsonNode invalid = update(event, Map.of("placeId", archived.get("id").asText(),
                "poiId", foreign.get("id").asText()), 422);
        assertThat(invalid.get("errors")).extracting(e -> e.get("path").asText() + ":" + e.get("code").asText())
                .contains("placeId:ARCHIVED_REFERENCE", "poiId:RESOURCE_NOT_FOUND");
    }

    // --- Occurrences -----------------------------------------------------------------------------

    @Test
    void occurrencesAreAddedChangedRemovedAndKeptInStartOrder() throws Exception {
        JsonNode event = createEvent(PROJECT_A, "Curator talk");
        JsonNode later = addOccurrence(event, "2026-11-12T10:00:00Z", "2026-11-12T11:00:00Z", 201);
        JsonNode earlier = addOccurrence(event, "2026-11-05T10:00:00Z", "2026-11-05T11:00:00Z", 201);
        assertThat(later.get("status").asText()).isEqualTo("SCHEDULED");

        mockMvc.perform(json(put(occurrencesUrl(event) + "/{o}", later.get("id").asText()), Map.of(
                        "startsAt", "2026-11-12T10:00:00Z", "endsAt", "2026-11-12T11:30:00Z", "status", "CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        JsonNode reloaded = reload(event);
        assertThat(reloaded.get("occurrences")).extracting(o -> o.get("id").asText())
                .containsExactly(earlier.get("id").asText(), later.get("id").asText());

        mockMvc.perform(authenticated(delete(occurrencesUrl(event) + "/{o}", earlier.get("id").asText())))
                .andExpect(status().isNoContent());
        assertThat(reload(event).get("occurrences")).hasSize(1);
    }

    @Test
    void anOccurrenceEndsAfterItStartsAndLastsAtMostAYear() throws Exception {
        JsonNode event = createEvent(PROJECT_A, "Curator talk");
        assertThat(addOccurrence(event, "2026-11-05T11:00:00Z", "2026-11-05T10:00:00Z", 422)
                .get("errors").get(0).get("code").asText()).isEqualTo("BEFORE_START");
        assertThat(addOccurrence(event, "2026-01-01T00:00:00Z", "2027-01-03T00:00:00Z", 422)
                .get("errors").get(0).get("code").asText()).isEqualTo("TOO_LONG");
    }

    @Test
    void theCalendarListsOverlappingOccurrencesAcrossEvents() throws Exception {
        JsonNode talk = createEvent(PROJECT_A, "Curator talk");
        JsonNode workshop = createEvent(PROJECT_A, "Workshop");
        addOccurrence(talk, "2026-11-05T10:00:00Z", "2026-11-05T11:00:00Z", 201);
        addOccurrence(workshop, "2026-11-04T23:00:00Z", "2026-11-05T01:00:00Z", 201);
        addOccurrence(workshop, "2026-12-01T10:00:00Z", "2026-12-01T11:00:00Z", 201);

        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/events/occurrences", PROJECT_A)
                        .param("from", "2026-11-05T00:00:00Z").param("to", "2026-11-06T00:00:00Z")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventId").value(workshop.get("id").asText()));
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/events/occurrences", PROJECT_A)
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2027-03-01T00:00:00Z")))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- Lifecycle and references ----------------------------------------------------------------

    @Test
    void activationNeedsOnlyTheTitle() throws Exception {
        JsonNode event = createEvent(PROJECT_A, "Curator talk");
        JsonNode untitled = update(event, Map.of("translations", Map.of("el", Map.of("title", " "))), 200);
        assertThat(changeStatus(eventsUrl(PROJECT_A), event.get("id").asText(), "ACTIVE", untitled.get("version").asLong(), 422)
                .get("errors").get(0).get("path").asText()).isEqualTo("translations.el.title");
        JsonNode titled = update(untitled, Map.of("translations", Map.of("el", Map.of("title", "Ομιλία"))), 200);
        changeStatus(eventsUrl(PROJECT_A), event.get("id").asText(), "ACTIVE", titled.get("version").asLong(), 200);
    }

    @Test
    void anEventKeepsItsPlacePoiAndRouteInUse() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Museum", "BUILDING");
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        JsonNode route = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes", PROJECT_A),
                        Map.of("title", "Trail", "type", "OUTDOOR"))).andExpect(status().isCreated()).andReturn());
        JsonNode event = createEvent(PROJECT_A, "Curator talk");
        update(event, Map.of("placeId", place.get("id").asText(), "poiId", poi.get("id").asText(),
                "routeId", route.get("id").asText()), 200);

        JsonNode inUse = changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ARCHIVED", place.get("version").asLong(), 409);
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("EVENT");
        assertThat(inUse.get("references").get(0).get("title").asText()).isEqualTo("Curator talk");
        changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ARCHIVED", poi.get("version").asLong(), 409);
        changeStatus("/api/v1/projects/" + PROJECT_A + "/routes", route.get("id").asText(), "ARCHIVED",
                route.get("version").asLong(), 409);
    }

    @Test
    void aGuideReferencingAnEventBlocksItsArchival() throws Exception {
        JsonNode event = createEvent(PROJECT_A, "Curator talk");
        JsonNode guide = read(mockMvc.perform(json(post("/api/v1/projects/{p}/guides", PROJECT_A), Map.of("title", "What's on")))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post("/api/v1/projects/{p}/guides/{g}/blocks", PROJECT_A, guide.get("id").asText()),
                        Map.of("blockType", "REFERENCE", "resourceType", "EVENT", "resourceId", event.get("id").asText())))
                .andExpect(status().isCreated());

        JsonNode inUse = changeStatus(eventsUrl(PROJECT_A), event.get("id").asText(), "ARCHIVED", event.get("version").asLong(), 409);
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("GUIDE");
        mockMvc.perform(authenticated(get(eventsUrl(PROJECT_A) + "/{id}/references", event.get("id").asText())))
                .andExpect(jsonPath("$[0].title").value("What's on"));
    }

    // --- Listing, order, and isolation -----------------------------------------------------------

    @Test
    void listsSearchesAndReorders() throws Exception {
        JsonNode first = createEvent(PROJECT_A, "Curator talk");
        JsonNode second = createEvent(PROJECT_A, "Family workshop");
        mockMvc.perform(authenticated(get(eventsUrl(PROJECT_A)).param("q", "WORKSHOP")))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(json(put(eventsUrl(PROJECT_A) + ":order"),
                        Map.of("ids", List.of(second.get("id").asText(), first.get("id").asText()))))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(eventsUrl(PROJECT_A))))
                .andExpect(jsonPath("$.content[0].id").value(second.get("id").asText()));
    }

    @Test
    void anotherProjectsEventAndOccurrenceAreInvisible() throws Exception {
        JsonNode foreign = createEvent(PROJECT_B, "Elsewhere");
        JsonNode foreignOccurrence = addOccurrence(foreign, "2026-11-05T10:00:00Z", "2026-11-05T11:00:00Z", 201);
        JsonNode own = createEvent(PROJECT_A, "Curator talk");
        mockMvc.perform(authenticated(get(eventsUrl(PROJECT_A) + "/{id}", foreign.get("id").asText())))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(delete(occurrencesUrl(own) + "/{o}", foreignOccurrence.get("id").asText())))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/events/occurrences", PROJECT_A)
                        .param("from", "2026-11-01T00:00:00Z").param("to", "2026-11-30T00:00:00Z")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode createEvent(UUID projectId, String title) throws Exception {
        return read(mockMvc.perform(json(post(eventsUrl(projectId)), Map.of("title", title)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode addOccurrence(JsonNode event, String startsAt, String endsAt, int expected) throws Exception {
        return read(mockMvc.perform(json(post(occurrencesUrl(event)), Map.of("startsAt", startsAt, "endsAt", endsAt)))
                .andExpect(status().is(expected)).andReturn());
    }

    private JsonNode reload(JsonNode event) throws Exception {
        return read(mockMvc.perform(authenticated(get(eventsUrl(UUID.fromString(event.get("projectId").asText()))
                        + "/{id}", event.get("id").asText())))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode update(JsonNode event, Map<String, Object> changes, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("version", event.get("version").asLong());
        body.put("slug", event.get("slug").asText());
        body.put("featured", false);
        body.putAll(changes);
        return read(mockMvc.perform(json(put(eventsUrl(PROJECT_A) + "/{id}", event.get("id").asText()), body))
                .andExpect(status().is(expected)).andReturn());
    }

    private static String eventsUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/events";
    }

    private static String occurrencesUrl(JsonNode event) {
        return eventsUrl(UUID.fromString(event.get("projectId").asText())) + "/" + event.get("id").asText() + "/occurrences";
    }
}
