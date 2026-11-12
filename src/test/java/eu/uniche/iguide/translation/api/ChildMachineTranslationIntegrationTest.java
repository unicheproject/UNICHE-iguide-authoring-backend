package eu.uniche.iguide.translation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * "Translate with MuseMind" and "mark reviewed" for Route Stops, Guide Blocks (the story's rich text), and Assets
 * ({@code 12} section 3, {@code 03} section 1; DECISIONS.md D-036), against the labelled mock gateway.
 */
class ChildMachineTranslationIntegrationTest extends ContentIntegrationTest {

    private static final String MOCK = "[MuseMind mock · el→en] ";

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    @Test
    void aStopOfADraftRouteIsTranslatedInPlaceAndReviewedByAHumanEdit() throws Exception {
        JsonNode route = route(PROJECT_A, false);
        JsonNode stop = route.get("stops").get(0);
        String stopUrl = stopUrl(PROJECT_A, route, stop);

        JsonNode translated = read(mockMvc.perform(json(post(stopUrl + "/en:translate"),
                        Map.of("version", route.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Working-Copy-Version"))
                .andReturn());
        JsonNode en = translated.get("stops").get(0).get("translations").get("en");
        assertThat(en.get("narrative").asText()).isEqualTo(MOCK + "Στάση");
        assertThat(en.get("titleOverride").asText()).isEqualTo(MOCK + "Είσοδος");
        assertThat(translated.get("version").asLong()).isEqualTo(route.get("version").asLong() + 1);

        JsonNode entry = provenance(PROJECT_A).get(0);
        assertThat(entry.get("resourceType").asText()).isEqualTo("ROUTE_STOP");
        assertThat(entry.get("resourceId").asText()).isEqualTo(stop.get("id").asText());
        assertThat(entry.get("parentType").asText()).isEqualTo("ROUTE");
        assertThat(entry.get("parentId").asText()).isEqualTo(route.get("id").asText());
        assertThat(entry.get("pending").asBoolean()).isFalse();
        assertThat(entry.get("provenance").get("reviewedAt").isNull()).isTrue();
        assertThat(provenance(PROJECT_B)).isEmpty();

        // Saving the same text is no review; changing it is.
        mockMvc.perform(json(put(stopUrl + "/en"), Map.of("version", translated.get("version").asLong(),
                        "narrative", MOCK + "Στάση", "titleOverride", MOCK + "Είσοδος")))
                .andExpect(status().isOk());
        assertThat(provenance(PROJECT_A).get(0).get("provenance").get("reviewedAt").isNull()).isTrue();
        JsonNode current = routeOf(PROJECT_A, route);
        mockMvc.perform(json(put(stopUrl + "/en"), Map.of("version", current.get("version").asLong(),
                        "narrative", "Stop", "titleOverride", "Entrance")))
                .andExpect(status().isOk());
        assertThat(provenance(PROJECT_A).get(0).get("provenance").get("reviewedAt").asText()).isNotBlank();

        // Emptying the language drops it.
        current = routeOf(PROJECT_A, route);
        mockMvc.perform(authenticated(delete(stopUrl + "/en").param("version", current.get("version").asText())))
                .andExpect(status().isOk());
        assertThat(provenance(PROJECT_A)).isEmpty();
        mockMvc.perform(authenticated(post(stopUrl + "/en:mark-reviewed")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStopOfAnActiveRouteIsCarriedByTheWorkingCopyUntilApply() throws Exception {
        JsonNode route = route(PROJECT_A, true);
        JsonNode stop = route.get("stops").get(0);
        String stopUrl = stopUrl(PROJECT_A, route, stop);

        MvcResult first = mockMvc.perform(json(post(stopUrl + "/en:translate"),
                        Map.of("version", route.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Working-Copy-Version"))
                .andExpect(jsonPath("$.hasWorkingCopy").value(true))
                .andExpect(jsonPath("$.stops[0].translations.en.narrative").value(MOCK + "Στάση"))
                .andReturn();
        long copyVersion = Long.parseLong(first.getResponse().getHeader("X-Working-Copy-Version"));
        JsonNode pending = provenance(PROJECT_A);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("pending").asBoolean()).isTrue();
        assertThat(pending.get(0).get("resourceType").asText()).isEqualTo("ROUTE_STOP");
        assertThat(pending.get(0).get("parentId").asText()).isEqualTo(route.get("id").asText());
        assertThat(warnings(PROJECT_A)).isEmpty();

        // Mark reviewed acts on what the copy carries, and moves no version.
        mockMvc.perform(authenticated(post(stopUrl + "/en:mark-reviewed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(true))
                .andExpect(jsonPath("$.parentType").value("ROUTE"))
                .andExpect(jsonPath("$.provenance.reviewedAt").isNotEmpty());
        JsonNode copy = read(mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "routes",
                        route.get("id").asText())))).andExpect(status().isOk()).andReturn());
        assertThat(copy.get("version").asLong()).isEqualTo(copyVersion);

        // A second machine translation is unreviewed again; apply carries it to the stop's row.
        mockMvc.perform(json(post(stopUrl + "/en:translate"), Map.of("version", route.get("version").asLong(),
                        "workingCopyVersion", copyVersion)))
                .andExpect(status().isOk());
        applyWorkingCopy(PROJECT_A, "routes", route.get("id").asText(), 200);
        JsonNode applied = provenance(PROJECT_A);
        assertThat(applied).hasSize(1);
        assertThat(applied.get(0).get("pending").asBoolean()).isFalse();
        JsonNode warning = warnings(PROJECT_A).get(0);
        assertThat(warning.get("resourceType").asText()).isEqualTo("ROUTE");
        assertThat(warning.get("resourceId").asText()).isEqualTo(route.get("id").asText());
        assertThat(warning.get("path").asText()).isEqualTo("stops[0].translations.en");

        mockMvc.perform(authenticated(post(stopUrl + "/en:mark-reviewed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false));
        assertThat(warnings(PROJECT_A)).isEmpty();
    }

    @Test
    void discardDropsWhatTheCopyCarriedForAStop() throws Exception {
        JsonNode route = route(PROJECT_A, true);
        String stopUrl = stopUrl(PROJECT_A, route, route.get("stops").get(0));
        mockMvc.perform(json(post(stopUrl + "/en:translate"), Map.of("version", route.get("version").asLong())))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(delete(workingCopyUrl(PROJECT_A, "routes", route.get("id").asText()))))
                .andExpect(status().is2xxSuccessful());
        assertThat(provenance(PROJECT_A)).isEmpty();
    }

    @Test
    void aStoryBlockKeepsItsNodesAndImagesWhenTranslated() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(10, 10));
        JsonNode guide = read(mockMvc.perform(json(post("/api/v1/projects/{p}/guides", PROJECT_A),
                        Map.of("title", "Ιστορία")))
                .andExpect(status().isCreated()).andReturn());
        JsonNode block = read(mockMvc.perform(json(post("/api/v1/projects/{p}/guides/{id}/blocks", PROJECT_A,
                        guide.get("id").asText()), Map.of("blockType", "TEXT", "translations", Map.of("el", Map.of(
                        "text", Map.of("blocks", List.of(
                                Map.of("type", "heading", "level", 2, "text", "Αρχή"),
                                Map.of("type", "asset", "assetId", asset.get("id").asText()),
                                Map.of("type", "paragraph", "text", "Κείμενο"))))))))
                .andExpect(status().isCreated()).andReturn());
        JsonNode current = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/guides/{id}", PROJECT_A,
                guide.get("id").asText()))).andReturn());
        String blockUrl = "/api/v1/projects/" + PROJECT_A + "/guides/" + guide.get("id").asText() + "/blocks/"
                + block.get("id").asText() + "/translations/en";

        JsonNode translated = read(mockMvc.perform(json(post(blockUrl + ":translate"),
                        Map.of("version", current.get("version").asLong())))
                .andExpect(status().isOk()).andReturn());
        JsonNode nodes = translated.get("blocks").get(0).get("translations").get("en").get("text").get("blocks");
        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0).get("type").asText()).isEqualTo("heading");
        assertThat(nodes.get(0).get("level").asInt()).isEqualTo(2);
        assertThat(nodes.get(0).get("text").asText()).isEqualTo(MOCK + "Αρχή");
        assertThat(nodes.get(1).get("type").asText()).isEqualTo("asset");
        assertThat(nodes.get(1).get("assetId").asText()).isEqualTo(asset.get("id").asText());
        assertThat(nodes.get(2).get("text").asText()).isEqualTo(MOCK + "Κείμενο");

        JsonNode entry = provenance(PROJECT_A).get(0);
        assertThat(entry.get("resourceType").asText()).isEqualTo("GUIDE_BLOCK");
        assertThat(entry.get("parentType").asText()).isEqualTo("GUIDE");
        mockMvc.perform(authenticated(post(blockUrl + ":mark-reviewed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("GUIDE_BLOCK"))
                .andExpect(jsonPath("$.pending").value(false));

        // Another Project cannot reach the block, and an unknown block is not found.
        mockMvc.perform(json(post(blockUrl.replace(PROJECT_A.toString(), PROJECT_B.toString()) + ":translate"),
                        Map.of("version", translated.get("version").asLong())))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/v1/projects/" + PROJECT_A + "/guides/" + guide.get("id").asText()
                        + "/blocks/" + UUID.randomUUID() + "/translations/en:translate"),
                        Map.of("version", translated.get("version").asLong())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmptyStopHasNothingToTranslate() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode route = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes", PROJECT_A),
                Map.of("title", "Διαδρομή", "type", "OUTDOOR"))).andExpect(status().isCreated()).andReturn());
        JsonNode stop = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes/{id}/stops", PROJECT_A,
                        route.get("id").asText()), Map.of("poiId", poi.get("id").asText())))
                .andExpect(status().isCreated()).andReturn());
        JsonNode current = routeOf(PROJECT_A, route);
        mockMvc.perform(json(post(stopUrl(PROJECT_A, route, stop) + "/en:translate"),
                        Map.of("version", current.get("version").asLong())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("NOTHING_TO_TRANSLATE"))
                .andExpect(jsonPath("$.errors[0].path").value("stops[0].translations.el"));
    }

    @Test
    void anAssetIsTranslatedInPlaceUnderItsVersion() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(10, 10));
        String url = "/api/v1/projects/" + PROJECT_A + "/assets/" + asset.get("id").asText() + "/translations/";
        JsonNode greek = read(mockMvc.perform(json(put(url + "el"), Map.of("version", asset.get("version").asLong(),
                        "title", "Κούρος", "alternativeText", "Μαρμάρινο άγαλμα")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(json(post(url + "en:translate"), Map.of("version", asset.get("version").asLong())))
                .andExpect(status().isConflict());
        JsonNode translated = read(mockMvc.perform(json(post(url + "en:translate"),
                        Map.of("version", greek.get("version").asLong())))
                .andExpect(status().isOk()).andReturn());
        assertThat(translated.get("translations").get("en").get("title").asText()).isEqualTo(MOCK + "Κούρος");
        assertThat(translated.get("translations").get("en").get("alternativeText").asText())
                .isEqualTo(MOCK + "Μαρμάρινο άγαλμα");
        assertThat(translated.get("version").asLong()).isGreaterThan(greek.get("version").asLong());

        JsonNode entry = provenance(PROJECT_A).get(0);
        assertThat(entry.get("resourceType").asText()).isEqualTo("ASSET");
        assertThat(entry.get("parentType").isNull()).isTrue();
        assertThat(entry.get("pending").asBoolean()).isFalse();

        // An ACTIVE Asset warns until reviewed; a human edit reviews it.
        JsonNode active = changeStatus(assetUrl(PROJECT_A), asset.get("id").asText(), "ACTIVE",
                translated.get("version").asLong(), 200);
        JsonNode warning = warnings(PROJECT_A).get(0);
        assertThat(warning.get("resourceType").asText()).isEqualTo("ASSET");
        assertThat(warning.get("path").asText()).isEqualTo("translations.en");
        mockMvc.perform(json(put(url + "en"), Map.of("version", active.get("version").asLong(),
                        "title", "Kouros", "alternativeText", "Marble statue")))
                .andExpect(status().isOk());
        assertThat(warnings(PROJECT_A)).isEmpty();
        assertThat(provenance(PROJECT_A).get(0).get("provenance").get("reviewedAt").asText()).isNotBlank();

        mockMvc.perform(authenticated(post("/api/v1/projects/{p}/assets/{id}/translations/en:mark-reviewed",
                        PROJECT_B, asset.get("id").asText())))
                .andExpect(status().isNotFound());
    }

    /** A Route with one stop on an ACTIVE Point of Interest, texts in Greek; ACTIVE when asked. */
    private JsonNode route(UUID projectId, boolean active) throws Exception {
        JsonNode poi = createPoi(projectId, "{\"title\": \"Κούρος\"}");
        changeStatus(poiUrl(projectId), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(), 200);
        JsonNode route = read(mockMvc.perform(json(post("/api/v1/projects/{p}/routes", projectId),
                Map.of("title", "Διαδρομή", "type", "OUTDOOR"))).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post("/api/v1/projects/{p}/routes/{id}/stops", projectId, route.get("id").asText()),
                        Map.of("poiId", poi.get("id").asText(), "translations",
                                Map.of("el", Map.of("narrative", "Στάση", "titleOverride", "Είσοδος")))))
                .andExpect(status().isCreated());
        JsonNode current = routeOf(projectId, route);
        if (!active) {
            return current;
        }
        changeStatus("/api/v1/projects/" + projectId + "/routes", route.get("id").asText(), "ACTIVE",
                current.get("version").asLong(), 200);
        return routeOf(projectId, route);
    }

    private JsonNode routeOf(UUID projectId, JsonNode route) throws Exception {
        return read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/routes/{id}", projectId,
                route.get("id").asText()))).andExpect(status().isOk()).andReturn());
    }

    private static String stopUrl(UUID projectId, JsonNode route, JsonNode stop) {
        return "/api/v1/projects/" + projectId + "/routes/" + route.get("id").asText() + "/stops/"
                + stop.get("id").asText() + "/translations";
    }

    private JsonNode provenance(UUID projectId) throws Exception {
        return read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/ai-provenance", projectId)))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode warnings(UUID projectId) throws Exception {
        JsonNode readiness = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/readiness", projectId)))
                .andExpect(status().isOk()).andReturn());
        var matching = objectMapper.createArrayNode();
        readiness.get("warnings").forEach(warning -> {
            if ("AI_OUTPUT_UNREVIEWED".equals(warning.get("code").asText())) {
                matching.add(warning);
            }
        });
        return matching;
    }
}
