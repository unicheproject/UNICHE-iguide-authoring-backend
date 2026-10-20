package eu.uniche.iguide.trigger.api;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Interaction Triggers on Points of Interest ({@code 03} section 14, {@code 08} Milestone 5). */
class TriggerIntegrationTest extends ContentIntegrationTest {

    private static final String BEACON_UUID = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0";

    private JsonNode poi;

    @BeforeEach
    void setUp() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
        poi = createPoi(PROJECT_A, "{\"title\": \"Kouros\"}");
    }

    @Test
    void eachTypeIsCreatedAsADraftWithItsDefaults() throws Exception {
        JsonNode qr = create(Map.of("triggerType", "QR_CODE"), 201);
        assertThat(qr.get("status").asText()).isEqualTo("DRAFT");
        assertThat(qr.get("code").asText()).matches("[a-z2-9]{8}");
        assertThat(qr.get("supportedSurfaces").isNull()).isTrue();

        JsonNode beacon = create(Map.of("triggerType", "BEACON",
                "configuration", Map.of("uuid", BEACON_UUID, "major", 1, "minor", 14)), 201);
        assertThat(beacon.get("configuration").get("uuid").asText()).isEqualTo(BEACON_UUID.toLowerCase());
        assertThat(beacon.get("configuration").get("cooldownSeconds").asInt()).isEqualTo(300);
        assertThat(beacon.get("configuration").get("proximityMeters").asDouble()).isEqualTo(2.0);
        assertThat(beacon.get("code").isNull()).isTrue();

        JsonNode geo = create(Map.of("triggerType", "GEO_PROXIMITY", "supportedSurfaces", List.of("MOBILE"),
                "configuration", Map.of("latitude", 37.1, "longitude", 25.4, "radiusMeters", 40)), 201);
        assertThat(geo.get("supportedSurfaces").get(0).asText()).isEqualTo("MOBILE");
        assertThat(geo.get("sortOrder").asInt()).isEqualTo(3);

        mockMvc.perform(authenticated(get(url(PROJECT_A)).param("targetResourceType", "POI")
                        .param("targetResourceId", poi.get("id").asText())))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].triggerType").value("QR_CODE"));
    }

    @Test
    void codesAndBeaconsAreUniqueInTheProject() throws Exception {
        create(Map.of("triggerType", "QR_CODE", "code", "Kouros-1"), 201);
        assertThat(create(Map.of("triggerType", "QR_CODE", "code", "Kouros-1"), 409).get("errors").get(0).get("path")
                .asText()).isEqualTo("code");
        assertThat(create(Map.of("triggerType", "QR_CODE", "code", "bad code!"), 422).get("errors").get(0).get("code")
                .asText()).isEqualTo("INVALID_FORMAT");

        Map<String, Object> beacon = Map.of("triggerType", "BEACON",
                "configuration", Map.of("uuid", BEACON_UUID, "major", 1, "minor", 14));
        create(beacon, 201);
        assertThat(create(beacon, 409).get("type").asText()).isEqualTo("DUPLICATE_VALUE");
    }

    @Test
    void settingsAreValidatedPerType() throws Exception {
        JsonNode bad = create(Map.of("triggerType", "BEACON", "code", "x",
                "configuration", Map.of("uuid", "not-a-uuid", "major", 70000, "minor", 1, "colour", "red")), 422);
        assertThat(bad.get("errors")).extracting(e -> e.get("path").asText())
                .contains("code", "configuration.uuid", "configuration.major", "configuration.colour");
        assertThat(create(Map.of("triggerType", "GEO_PROXIMITY",
                "configuration", Map.of("latitude", 37.1, "longitude", 25.4, "radiusMeters", 2)), 422)
                .get("errors").get(0).get("path").asText()).isEqualTo("configuration.radiusMeters");
        assertThat(create(Map.of("triggerType", "BEACON", "supportedSurfaces", List.of("WEB"),
                "configuration", Map.of("uuid", BEACON_UUID, "major", 2, "minor", 2)), 422)
                .get("errors").get(0).get("code").asText()).isEqualTo("UNSUPPORTED_SURFACE");
        assertThat(create(Map.of("triggerType", "CAMERA_RECOGNITION"), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("UNSUPPORTED_TYPE");
        assertThat(create(Map.of("triggerType", "MANUAL"), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("UNSUPPORTED_TYPE");
    }

    @Test
    void theTargetIsAPointOfInterestOfTheProjectThatIsNotArchived() throws Exception {
        JsonNode foreign = createPoi(PROJECT_B, "{\"title\": \"Elsewhere\"}");
        Map<String, Object> body = new HashMap<>(Map.of("targetResourceType", "POI",
                "targetResourceId", foreign.get("id").asText(), "triggerType", "QR_CODE"));
        mockMvc.perform(json(post(url(PROJECT_A)), body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));

        JsonNode trigger = create(Map.of("triggerType", "QR_CODE"), 201);
        mockMvc.perform(authenticated(get(url(PROJECT_B) + "/{id}", trigger.get("id").asText())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anActiveTriggerKeepsItsPointOfInterestInUseAndIsEditedInPlace() throws Exception {
        JsonNode trigger = create(Map.of("triggerType", "BEACON",
                "configuration", Map.of("uuid", BEACON_UUID, "major", 1, "minor", 14)), 201);
        JsonNode active = changeStatus(url(PROJECT_A), trigger.get("id").asText(), "ACTIVE", trigger.get("version").asLong(), 200);

        JsonNode edited = read(mockMvc.perform(json(put(url(PROJECT_A) + "/{id}", trigger.get("id").asText()),
                        Map.of("version", active.get("version").asLong(),
                                "configuration", Map.of("uuid", BEACON_UUID, "major", 1, "minor", 15))))
                .andExpect(status().isOk()).andReturn());
        assertThat(edited.get("configuration").get("minor").asInt()).isEqualTo(15);
        assertThat(edited.get("status").asText()).isEqualTo("ACTIVE");

        JsonNode inUse = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ARCHIVED", poi.get("version").asLong(), 409);
        assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("TRIGGER");
        assertThat(inUse.get("references").get(0).get("title").asText()).contains("Bluetooth beacon");

        JsonNode archived = changeStatus(url(PROJECT_A), trigger.get("id").asText(), "ARCHIVED", edited.get("version").asLong(), 200);
        mockMvc.perform(json(put(url(PROJECT_A) + "/{id}", trigger.get("id").asText()),
                        Map.of("version", archived.get("version").asLong(), "configuration", Map.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
        changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ARCHIVED", poi.get("version").asLong(), 200);
    }

    @Test
    void theQrImageWaitsForThePublicWebAddress() throws Exception {
        JsonNode qr = create(Map.of("triggerType", "QR_CODE"), 201);
        mockMvc.perform(authenticated(get(url(PROJECT_A) + "/{id}/qr.png", qr.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("NO_PUBLIC_BASE_URL"));
    }

    private JsonNode create(Map<String, Object> fields, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>(fields);
        body.put("targetResourceType", "POI");
        body.put("targetResourceId", poi.get("id").asText());
        return read(mockMvc.perform(json(post(url(PROJECT_A)), body)).andExpect(status().is(expected)).andReturn());
    }

    private static String url(java.util.UUID projectId) {
        return "/api/v1/projects/" + projectId + "/triggers";
    }
}
