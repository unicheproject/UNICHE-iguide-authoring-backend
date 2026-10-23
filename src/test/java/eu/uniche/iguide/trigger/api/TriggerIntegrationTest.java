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

/**
 * Interaction Triggers ({@code 03} section 14, {@code 08} Milestone 5): on Points of Interest, and since D-028 on
 * Places, Routes, Guides, and Events.
 */
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
    void theQrImageEncodesTheTargetsRuntimeUrlOnceTheAddressIsSet() throws Exception {
        JsonNode poiQr = create(Map.of("triggerType", "QR_CODE", "code", "IGUIDE-TEST-0001"), 201);
        JsonNode route = createTarget(PROJECT_A, "ROUTE");
        JsonNode routeQr = createFor("ROUTE", route.get("id").asText(), 201);
        assertThat(poiQr.get("runtimeUrl").isNull()).isTrue();

        web(true, "https://guide.museum.gr/");
        String poiUrl = "https://guide.museum.gr/el/pois/" + poi.get("slug").asText() + "?t=IGUIDE-TEST-0001";
        mockMvc.perform(authenticated(get(url(PROJECT_A) + "/{id}", poiQr.get("id").asText())))
                .andExpect(jsonPath("$.runtimeUrl").value(poiUrl));
        assertThat(decode(qr(poiQr, 200))).isEqualTo(poiUrl);
        assertThat(decode(qr(routeQr, 200))).isEqualTo("https://guide.museum.gr/el/routes/" + route.get("slug").asText()
                + "?t=" + routeQr.get("code").asText());
        mockMvc.perform(authenticated(get(url(PROJECT_A)).param("targetResourceType", "ROUTE")))
                .andExpect(jsonPath("$.content[0].runtimeUrl").value(org.hamcrest.Matchers.startsWith(
                        "https://guide.museum.gr/el/routes/")));

        // Beacons have no QR image, and a disabled WEB surface hides the address again.
        JsonNode beacon = create(Map.of("triggerType", "BEACON",
                "configuration", Map.of("uuid", BEACON_UUID, "major", 1, "minor", 14)), 201);
        assertThat(beacon.get("runtimeUrl").isNull()).isTrue();
        web(false, "https://guide.museum.gr");
        mockMvc.perform(authenticated(get(url(PROJECT_A) + "/{id}/qr.png", poiQr.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("NO_PUBLIC_BASE_URL"));
    }

    private void web(boolean enabled, String publicBaseUrl) throws Exception {
        mockMvc.perform(json(put("/api/v1/projects/{p}/surfaces", PROJECT_A), Map.of("surfaces", List.of(Map.of(
                        "surface", "WEB", "enabled", enabled, "configuration", Map.of("publicBaseUrl", publicBaseUrl))))))
                .andExpect(status().isOk());
    }

    private byte[] qr(JsonNode trigger, int expected) throws Exception {
        return mockMvc.perform(authenticated(get(url(PROJECT_A) + "/{id}/qr.png", trigger.get("id").asText())))
                .andExpect(status().is(expected))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** Reads a QR image back with ZXing, as a phone camera would. */
    private static String decode(byte[] png) throws Exception {
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(512);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        var source = new com.google.zxing.RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
        return new com.google.zxing.qrcode.QRCodeReader()
                .decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source)))
                .getText();
    }

    @Test
    void theQrImageWaitsForThePublicWebAddress() throws Exception {
        JsonNode qr = create(Map.of("triggerType", "QR_CODE"), 201);
        mockMvc.perform(authenticated(get(url(PROJECT_A) + "/{id}/qr.png", qr.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("NO_PUBLIC_BASE_URL"));
    }

    // --- Other target kinds (DECISIONS.md D-028) ---------------------------------------------------

    /** The collection path and a minimal creation body of each new target kind. */
    private static final Map<String, String> KINDS = Map.of(
            "PLACE", "places:{\"title\": \"Agora\", \"type\": \"SITE\"}",
            "ROUTE", "routes:{\"title\": \"Trail\", \"type\": \"OUTDOOR\"}",
            "GUIDE", "guides:{\"title\": \"Story\"}",
            "EVENT", "events:{\"title\": \"Concert\"}");

    private JsonNode createTarget(java.util.UUID projectId, String kind) throws Exception {
        String[] spec = KINDS.get(kind).split(":", 2);
        return read(mockMvc.perform(json(post("/api/v1/projects/{p}/" + spec[0], projectId), spec[1]))
                .andExpect(status().isCreated()).andReturn());
    }

    private static String targetUrl(java.util.UUID projectId, String kind) {
        return "/api/v1/projects/" + projectId + "/" + KINDS.get(kind).split(":", 2)[0];
    }

    private JsonNode createFor(String kind, Object targetId, int expected) throws Exception {
        return read(mockMvc.perform(json(post(url(PROJECT_A)), Map.of("triggerType", "QR_CODE",
                "targetResourceType", kind, "targetResourceId", targetId.toString())))
                .andExpect(status().is(expected)).andReturn());
    }

    @Test
    void placesRoutesGuidesAndEventsAreTargetsToo() throws Exception {
        for (String kind : List.of("PLACE", "ROUTE", "GUIDE", "EVENT")) {
            JsonNode target = createTarget(PROJECT_A, kind);
            JsonNode trigger = createFor(kind, target.get("id").asText(), 201);
            assertThat(trigger.get("targetResourceType").asText()).isEqualTo(kind);
            changeStatus(url(PROJECT_A), trigger.get("id").asText(), "ACTIVE", trigger.get("version").asLong(), 200);

            mockMvc.perform(authenticated(get(url(PROJECT_A)).param("targetResourceType", kind)
                            .param("targetResourceId", target.get("id").asText())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(trigger.get("id").asText()));
        }
    }

    @Test
    void aTargetOfEveryKindIsInTheProjectAndNotArchived() throws Exception {
        JsonNode unknown = createFor("ROUTE", java.util.UUID.randomUUID(), 422);
        assertThat(unknown.get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(unknown.get("errors").get(0).get("message").asText()).contains("route");

        JsonNode foreign = createTarget(PROJECT_B, "EVENT");
        assertThat(createFor("EVENT", foreign.get("id").asText(), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("RESOURCE_NOT_FOUND");

        JsonNode guide = createTarget(PROJECT_A, "GUIDE");
        changeStatus(targetUrl(PROJECT_A, "GUIDE"), guide.get("id").asText(), "ARCHIVED", guide.get("version").asLong(), 200);
        assertThat(createFor("GUIDE", guide.get("id").asText(), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("ARCHIVED_REFERENCE");

        // A target of another kind with the same id is not found: the kind decides where to look.
        JsonNode place = createTarget(PROJECT_A, "PLACE");
        createFor("ROUTE", place.get("id").asText(), 422);
    }

    @Test
    void aTriggerKeepsItsPlaceRouteGuideOrEventInUse() throws Exception {
        for (String kind : List.of("PLACE", "ROUTE", "GUIDE", "EVENT")) {
            JsonNode target = createTarget(PROJECT_A, kind);
            String targetId = target.get("id").asText();
            JsonNode trigger = createFor(kind, targetId, 201);

            JsonNode inUse = changeStatus(targetUrl(PROJECT_A, kind), targetId, "ARCHIVED", target.get("version").asLong(), 409);
            assertThat(inUse.get("type").asText()).isEqualTo("RESOURCE_IN_USE");
            assertThat(inUse.get("references").get(0).get("resourceType").asText()).isEqualTo("TRIGGER");
            assertThat(inUse.get("references").get(0).get("resourceId").asText()).isEqualTo(trigger.get("id").asText());
            mockMvc.perform(authenticated(get(targetUrl(PROJECT_A, kind) + "/{id}/references", targetId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.resourceType == 'TRIGGER')].resourceId").value(trigger.get("id").asText()));

            changeStatus(url(PROJECT_A), trigger.get("id").asText(), "ARCHIVED", trigger.get("version").asLong(), 200);
            changeStatus(targetUrl(PROJECT_A, kind), targetId, "ARCHIVED", target.get("version").asLong(), 200);
        }
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
