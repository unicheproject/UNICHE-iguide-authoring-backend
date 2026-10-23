package eu.uniche.iguide.event.api;

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

/**
 * The working copy of an ACTIVE Event ({@code 03} section 1, section 15): fields, texts, links, occurrences, and
 * media change only on apply.
 */
class EventWorkingCopyIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    @Test
    void aSaveLeavesTheLiveEventAndItsOccurrencesUnchanged() throws Exception {
        JsonNode event = activeEventWithTwoOccurrences();
        List<Map<String, Object>> occurrences = List.of(
                occurrence(null, "2026-12-01T10:00:00Z", "2026-12-01T11:00:00Z"));

        JsonNode copy = saveWorkingCopy(PROJECT_A, "events", id(event), event.get("version").asLong(), null,
                payload(event, "Evening talk", Map.of(), occurrences, List.of()), 200);
        assertThat(copy.get("resource").get("translations").get("el").get("title").asText()).isEqualTo("Evening talk");
        assertThat(copy.get("resource").get("occurrences")).hasSize(1);

        JsonNode live = reload(event);
        assertThat(live.get("translations").get("el").get("title").asText()).isEqualTo("Curator talk");
        assertThat(live.get("occurrences")).hasSize(2);
        assertThat(live.get("version").asLong()).isEqualTo(event.get("version").asLong());
        assertThat(live.get("hasWorkingCopy").asBoolean()).isTrue();
    }

    @Test
    void applyRewritesFieldsLinksOccurrencesAndMediaThenDeletesTheCopy() throws Exception {
        JsonNode event = activeEventWithTwoOccurrences();
        JsonNode kept = event.get("occurrences").get(0);
        JsonNode place = createPlace(PROJECT_A, "Museum", "BUILDING");
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
        JsonNode route = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes", PROJECT_A),
                Map.of("title", "Trail", "type", "OUTDOOR"))).andExpect(status().isCreated()).andReturn());
        JsonNode image = uploadAsset(PROJECT_A, "talk.png", "image/png", png(8, 8));

        List<Map<String, Object>> occurrences = List.of(
                occurrence(kept.get("id").asText(), "2026-11-05T10:00:00Z", "2026-11-05T12:00:00Z"),
                occurrence(null, "2026-12-01T10:00:00Z", "2026-12-01T11:00:00Z"));
        Map<String, Object> links = Map.of("placeId", id(place), "poiId", id(poi), "routeId", id(route),
                "bookingUrl", "https://tickets.example.org/talk");
        saveWorkingCopy(PROJECT_A, "events", id(event), event.get("version").asLong(), null,
                payload(event, "Evening talk", links, occurrences,
                        List.of(Map.of("assetId", id(image), "usage", "HERO_IMAGE"))), 200);

        JsonNode applied = applyWorkingCopy(PROJECT_A, "events", id(event), 200);
        assertThat(applied.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(applied.get("hasWorkingCopy").asBoolean()).isFalse();
        assertThat(applied.get("translations").get("el").get("title").asText()).isEqualTo("Evening talk");
        assertThat(applied.get("placeId").asText()).isEqualTo(id(place));
        assertThat(applied.get("poiId").asText()).isEqualTo(id(poi));
        assertThat(applied.get("routeId").asText()).isEqualTo(id(route));
        assertThat(applied.get("bookingUrl").asText()).isEqualTo("https://tickets.example.org/talk");
        assertThat(applied.get("occurrences")).hasSize(2);
        assertThat(applied.get("occurrences").get(0).get("id").asText()).isEqualTo(kept.get("id").asText());
        assertThat(applied.get("occurrences").get(0).get("endsAt").asText()).isEqualTo("2026-11-05T12:00:00Z");
        assertThat(applied.get("occurrences").get(1).get("startsAt").asText()).isEqualTo("2026-12-01T10:00:00Z");
        assertThat(applied.get("media")).hasSize(1);
        assertThat(applied.get("media").get(0).get("assetId").asText()).isEqualTo(id(image));
        assertThat(applied.get("media").get(0).get("usage").asText()).isEqualTo("HERO_IMAGE");

        JsonNode live = reload(event);
        assertThat(live.get("occurrences")).extracting(o -> o.get("startsAt").asText())
                .containsExactly("2026-11-05T10:00:00Z", "2026-12-01T10:00:00Z");
        assertThat(live.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "events", id(event)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOccurrenceErrorIsReportedAtItsPositionInThePayload() throws Exception {
        JsonNode event = activeEventWithTwoOccurrences();
        JsonNode invalid = saveWorkingCopy(PROJECT_A, "events", id(event), event.get("version").asLong(), null,
                payload(event, "Curator talk", Map.of(),
                        List.of(occurrence(null, "2026-12-01T11:00:00Z", "2026-12-01T10:00:00Z")), List.of()), 422);
        assertThat(invalid.get("errors")).extracting(e -> e.get("path").asText() + ":" + e.get("code").asText())
                .contains("occurrences[0].endsAt:BEFORE_START");
        assertThat(reload(event).get("hasWorkingCopy").asBoolean()).isFalse();
    }

    @Test
    void anotherProjectsPlaceIsRejected() throws Exception {
        JsonNode event = activeEventWithTwoOccurrences();
        JsonNode foreign = createPlace(PROJECT_B, "Far away", "BUILDING");
        JsonNode invalid = saveWorkingCopy(PROJECT_A, "events", id(event), event.get("version").asLong(), null,
                payload(event, "Curator talk", Map.of("placeId", id(foreign)), List.of(), List.of()), 422);
        assertThat(invalid.get("errors")).extracting(e -> e.get("path").asText() + ":" + e.get("code").asText())
                .contains("placeId:RESOURCE_NOT_FOUND");
        saveWorkingCopy(PROJECT_B, "events", id(event), event.get("version").asLong(), null,
                payload(event, "Curator talk", Map.of(), List.of(), List.of()), 404);
    }

    @Test
    void pendingListsOnlyEventsWithACopyAndDeactivationDropsIt() throws Exception {
        JsonNode event = activeEventWithTwoOccurrences();
        createEvent(PROJECT_A, "Other talk");
        saveWorkingCopy(PROJECT_A, "events", id(event), event.get("version").asLong(), null,
                payload(event, "Evening talk", Map.of(), List.of(), List.of()), 200);

        mockMvc.perform(authenticated(get(eventsUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id(event)))
                .andExpect(jsonPath("$.content[0].hasWorkingCopy").value(true))
                .andExpect(jsonPath("$.content[0].workingCopyUpdatedAt").isNotEmpty());

        JsonNode draft = changeStatus(eventsUrl(PROJECT_A), id(event), "DRAFT", event.get("version").asLong(), 200);
        assertThat(draft.get("hasWorkingCopy").asBoolean()).isFalse();
        assertThat(draft.get("occurrences")).hasSize(2);
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "events", id(event)))))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get(eventsUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anActiveEventsOccurrencesAreNotEditedDirectly() throws Exception {
        JsonNode event = activeEventWithTwoOccurrences();
        String occurrences = eventsUrl(PROJECT_A) + "/" + id(event) + "/occurrences";
        mockMvc.perform(json(post(occurrences), Map.of("startsAt", "2026-12-01T10:00:00Z",
                        "endsAt", "2026-12-01T11:00:00Z")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
        mockMvc.perform(json(put(occurrences + "/{o}", event.get("occurrences").get(0).get("id").asText()),
                        Map.of("startsAt", "2026-12-01T10:00:00Z", "endsAt", "2026-12-01T11:00:00Z")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode activeEventWithTwoOccurrences() throws Exception {
        JsonNode event = createEvent(PROJECT_A, "Curator talk");
        String occurrences = eventsUrl(PROJECT_A) + "/" + id(event) + "/occurrences";
        for (String day : List.of("2026-11-05", "2026-11-12")) {
            mockMvc.perform(json(post(occurrences), Map.of("startsAt", day + "T10:00:00Z", "endsAt", day + "T11:00:00Z")))
                    .andExpect(status().isCreated());
        }
        JsonNode draft = reload(event);
        return changeStatus(eventsUrl(PROJECT_A), id(event), "ACTIVE", draft.get("version").asLong(), 200);
    }

    private JsonNode createEvent(UUID projectId, String title) throws Exception {
        return read(mockMvc.perform(json(post(eventsUrl(projectId)), Map.of("title", title)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode reload(JsonNode event) throws Exception {
        return read(mockMvc.perform(authenticated(get(eventsUrl(PROJECT_A) + "/{id}", id(event))))
                .andExpect(status().isOk()).andReturn());
    }

    private static Map<String, Object> payload(JsonNode event, String title, Map<String, Object> fields,
                                               List<Map<String, Object>> occurrences,
                                               List<Map<String, Object>> media) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("slug", event.get("slug").asText());
        payload.put("featured", false);
        payload.put("translations", Map.of("el", Map.of("title", title)));
        payload.put("occurrences", new ArrayList<>(occurrences));
        payload.put("media", new ArrayList<>(media));
        payload.putAll(fields);
        return payload;
    }

    private static Map<String, Object> occurrence(String id, String startsAt, String endsAt) {
        Map<String, Object> occurrence = new HashMap<>();
        occurrence.put("id", id);
        occurrence.put("startsAt", startsAt);
        occurrence.put("endsAt", endsAt);
        return occurrence;
    }

    private static String eventsUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/events";
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
