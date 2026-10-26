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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * "Translate with MuseMind", AI provenance, and "mark reviewed" ({@code 12} section 3, {@code 03} section 1;
 * DECISIONS.md D-035), against the labelled mock gateway the test profile configures.
 */
class MachineTranslationIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    @Test
    void theStatusReportsTheMock() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/ai/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translation").value("MOCK"));
        mockMvc.perform(get("/api/v1/ai/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDraftIsTranslatedInPlaceWithUnreviewedProvenance() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        JsonNode current = read(mockMvc.perform(json(put(url(PROJECT_A, "places", place, "el", null)),
                        Map.of("version", place.get("version").asLong(), "title", "Ακρόπολη",
                                "summary", "Ο ιερός βράχος")))
                .andExpect(status().isOk()).andReturn());

        JsonNode translated = read(mockMvc.perform(json(post(url(PROJECT_A, "places", place, "en", "translate")),
                        Map.of("version", current.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Working-Copy-Version"))
                .andReturn());
        assertThat(translated.get("translations").get("en").get("title").asText())
                .isEqualTo("[MuseMind mock · el→en] Ακρόπολη");
        assertThat(translated.get("translations").get("en").get("summary").asText())
                .isEqualTo("[MuseMind mock · el→en] Ο ιερός βράχος");
        assertThat(translated.get("version").asLong()).isEqualTo(current.get("version").asLong() + 1);

        JsonNode provenance = provenance(PROJECT_A);
        assertThat(provenance).hasSize(1);
        JsonNode entry = provenance.get(0);
        assertThat(entry.get("resourceType").asText()).isEqualTo("PLACE");
        assertThat(entry.get("resourceId").asText()).isEqualTo(place.get("id").asText());
        assertThat(entry.get("languageCode").asText()).isEqualTo("en");
        assertThat(entry.get("pending").asBoolean()).isFalse();
        JsonNode recorded = entry.get("provenance");
        assertThat(recorded.get("origin").asText()).isEqualTo("AI_GENERATED");
        assertThat(recorded.get("serviceId").asText()).isEqualTo("musemind-mock");
        assertThat(recorded.get("function").asText()).isEqualTo("translate");
        assertThat(recorded.get("requestId").asText()).isNotBlank();
        assertThat(recorded.get("sourceLanguage").asText()).isEqualTo("el");
        assertThat(recorded.get("citations")).isEmpty();
        assertThat(recorded.get("generatedAt").asText()).isNotBlank();
        assertThat(recorded.get("reviewedBySubject").isNull()).isTrue();
        assertThat(recorded.get("reviewedAt").isNull()).isTrue();

        assertThat(provenance(PROJECT_B)).isEmpty();
    }

    @Test
    void theDefaultLanguageAnEmptySourceAndAStaleVersionAreRefused() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        long version = place.get("version").asLong();
        mockMvc.perform(json(post(url(PROJECT_A, "places", place, "el", "translate")), Map.of("version", version)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("DEFAULT_LANGUAGE"));
        mockMvc.perform(json(post(url(PROJECT_A, "places", place, "fr", "translate")), Map.of("version", version)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_LANGUAGE"));
        mockMvc.perform(json(post(url(PROJECT_A, "places", place, "en", "translate")), Map.of("version", version + 7)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(version));
        mockMvc.perform(json(post(url(PROJECT_B, "places", place, "en", "translate")), Map.of("version", version)))
                .andExpect(status().isNotFound());

        // A Guide's own texts are its title and summary.
        JsonNode guide = read(mockMvc.perform(json(post("/api/v1/projects/{p}/guides", PROJECT_A),
                Map.of("title", "Ιστορία"))).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(json(post(url(PROJECT_A, "guides", guide, "en", "translate")),
                        Map.of("version", guide.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.en.title").value("[MuseMind mock · el→en] Ιστορία"));

        // A DRAFT may have no default-language text at all.
        JsonNode emptied = read(mockMvc.perform(json(put(url(PROJECT_A, "places", place, "el", null)),
                        Map.of("version", version, "title", "")))
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(json(post(url(PROJECT_A, "places", place, "en", "translate")),
                        Map.of("version", emptied.get("version").asLong())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("NOTHING_TO_TRANSLATE"))
                .andExpect(jsonPath("$.errors[0].path").value("translations.el"));
    }

    @Test
    void markReviewedAndAHumanEditClearTheUnreviewedState() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        mockMvc.perform(authenticated(post(url(PROJECT_A, "places", place, "en", "mark-reviewed"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        JsonNode translated = machineTranslate(PROJECT_A, "places", place, place.get("version").asLong(), null);

        JsonNode reviewed = read(mockMvc.perform(json(post(url(PROJECT_A, "places", place, "en", "mark-reviewed")),
                        Map.of()))
                .andExpect(status().isOk()).andReturn());
        assertThat(reviewed.get("resourceType").asText()).isEqualTo("PLACE");
        assertThat(reviewed.get("languageCode").asText()).isEqualTo("en");
        assertThat(reviewed.get("pending").asBoolean()).isFalse();
        assertThat(reviewed.get("provenance").get("origin").asText()).isEqualTo("AI_GENERATED");
        assertThat(reviewed.get("provenance").get("reviewedBySubject").asText()).isNotBlank();
        assertThat(reviewed.get("provenance").get("reviewedAt").asText()).isNotBlank();
        // The text is unchanged and so is the version.
        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A) + "/{id}", place.get("id").asText())))
                .andExpect(jsonPath("$.version").value(translated.get("version").asLong()))
                .andExpect(jsonPath("$.translations.en.title").value("[MuseMind mock · el→en] Ακρόπολη"));

        // A second machine translation is unreviewed again; a person's change to the text reviews it.
        JsonNode again = machineTranslate(PROJECT_A, "places", place, translated.get("version").asLong(), null);
        assertThat(provenance(PROJECT_A).get(0).get("provenance").get("reviewedAt").isNull()).isTrue();
        JsonNode same = read(mockMvc.perform(json(put(url(PROJECT_A, "places", place, "en", null)),
                        Map.of("version", again.get("version").asLong(),
                                "title", "[MuseMind mock · el→en] Ακρόπολη ")))
                .andExpect(status().isOk()).andReturn());
        assertThat(provenance(PROJECT_A).get(0).get("provenance").get("reviewedAt").isNull()).isTrue();
        mockMvc.perform(json(put(url(PROJECT_A, "places", place, "en", null)),
                        Map.of("version", same.get("version").asLong(), "title", "Acropolis")))
                .andExpect(status().isOk());
        JsonNode edited = provenance(PROJECT_A).get(0).get("provenance");
        assertThat(edited.get("origin").asText()).isEqualTo("AI_GENERATED");
        assertThat(edited.get("reviewedAt").asText()).isNotBlank();
    }

    @Test
    void anActiveResourceCarriesProvenanceInItsWorkingCopyUntilApply() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode active = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(),
                200);
        long live = active.get("version").asLong();

        MvcResult first = mockMvc.perform(json(post(url(PROJECT_A, "pois", poi, "en", "translate")),
                        Map.of("version", live)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Working-Copy-Version"))
                .andExpect(jsonPath("$.hasWorkingCopy").value(true))
                .andExpect(jsonPath("$.translations.en.title").value("[MuseMind mock · el→en] Κούρος"))
                .andReturn();
        long copyVersion = Long.parseLong(first.getResponse().getHeader("X-Working-Copy-Version"));

        JsonNode pending = provenance(PROJECT_A);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("pending").asBoolean()).isTrue();
        assertThat(pending.get(0).get("resourceType").asText()).isEqualTo("POI");
        // Not live yet, so readiness does not warn.
        assertThat(warnings(PROJECT_A, "AI_OUTPUT_UNREVIEWED")).isEmpty();

        // Recording provenance leaves the copy's version alone, and an editor save of the copy keeps it.
        JsonNode copy = read(mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "pois",
                        poi.get("id").asText()))))
                .andExpect(status().isOk()).andReturn());
        assertThat(copy.get("version").asLong()).isEqualTo(copyVersion);
        JsonNode saved = saveWorkingCopy(PROJECT_A, "pois", poi.get("id").asText(), live, copyVersion,
                copy.get("payload"), 200);
        assertThat(provenance(PROJECT_A).get(0).get("pending").asBoolean()).isTrue();

        applyWorkingCopy(PROJECT_A, "pois", poi.get("id").asText(), 200);
        JsonNode applied = provenance(PROJECT_A);
        assertThat(applied).hasSize(1);
        assertThat(applied.get(0).get("pending").asBoolean()).isFalse();
        assertThat(applied.get(0).get("provenance").get("serviceId").asText()).isEqualTo("musemind-mock");
        assertThat(saved.get("version").asLong()).isGreaterThan(copyVersion);

        JsonNode warning = warnings(PROJECT_A, "AI_OUTPUT_UNREVIEWED").get(0);
        assertThat(warning.get("resourceType").asText()).isEqualTo("POI");
        assertThat(warning.get("resourceId").asText()).isEqualTo(poi.get("id").asText());
        assertThat(warning.get("languageCode").asText()).isEqualTo("en");
        assertThat(warning.get("path").asText()).isEqualTo("translations.en");

        mockMvc.perform(authenticated(post(url(PROJECT_A, "pois", poi, "en", "mark-reviewed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false));
        assertThat(warnings(PROJECT_A, "AI_OUTPUT_UNREVIEWED")).isEmpty();
    }

    @Test
    void discardDropsWhatTheCopyCarriedAndMarkReviewedActsOnIt() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode active = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(),
                200);
        long live = active.get("version").asLong();
        machineTranslate(PROJECT_A, "pois", poi, live, null);

        mockMvc.perform(authenticated(post(url(PROJECT_A, "pois", poi, "en", "mark-reviewed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(true))
                .andExpect(jsonPath("$.provenance.reviewedAt").isNotEmpty());

        mockMvc.perform(authenticated(delete(workingCopyUrl(PROJECT_A, "pois", poi.get("id").asText()))))
                .andExpect(status().is2xxSuccessful());
        assertThat(provenance(PROJECT_A)).isEmpty();
        mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", poi.get("id").asText())))
                .andExpect(jsonPath("$.translations.en").doesNotExist());
    }

    @Test
    void aHumanEditOfAnActiveResourceReviewsOnApply() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        JsonNode active = changeStatus(poiUrl(PROJECT_A), poi.get("id").asText(), "ACTIVE", poi.get("version").asLong(),
                200);
        long live = active.get("version").asLong();
        machineTranslate(PROJECT_A, "pois", poi, live, null);
        applyWorkingCopy(PROJECT_A, "pois", poi.get("id").asText(), 200);
        assertThat(warnings(PROJECT_A, "AI_OUTPUT_UNREVIEWED")).hasSize(1);

        JsonNode current = read(mockMvc.perform(authenticated(get(poiUrl(PROJECT_A) + "/{id}", poi.get("id").asText())))
                .andReturn());
        mockMvc.perform(json(put(url(PROJECT_A, "pois", poi, "en", null)),
                        Map.of("version", current.get("version").asLong(), "title", "Kouros")))
                .andExpect(status().isOk());
        JsonNode both = provenance(PROJECT_A);
        assertThat(both).hasSize(2);
        assertThat(both.get(0).get("pending").asBoolean()).isFalse();
        assertThat(both.get(0).get("provenance").get("reviewedAt").isNull()).isTrue();
        assertThat(both.get(1).get("pending").asBoolean()).isTrue();
        assertThat(both.get(1).get("provenance").get("reviewedAt").asText()).isNotBlank();

        applyWorkingCopy(PROJECT_A, "pois", poi.get("id").asText(), 200);
        assertThat(warnings(PROJECT_A, "AI_OUTPUT_UNREVIEWED")).isEmpty();
        assertThat(provenance(PROJECT_A).get(0).get("provenance").get("reviewedAt").asText()).isNotBlank();
    }

    @Test
    void emptyingALanguageDropsItsProvenance() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        JsonNode translated = machineTranslate(PROJECT_A, "places", place, place.get("version").asLong(), null);
        mockMvc.perform(authenticated(delete(url(PROJECT_A, "places", place, "en", null))
                        .param("version", translated.get("version").asText())))
                .andExpect(status().isOk());
        assertThat(provenance(PROJECT_A)).isEmpty();
        mockMvc.perform(authenticated(post(url(PROJECT_A, "places", place, "en", "mark-reviewed"))))
                .andExpect(status().isNotFound());
    }

    private JsonNode machineTranslate(UUID projectId, String kind, JsonNode resource, long version,
                                      Long workingCopyVersion) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("workingCopyVersion", workingCopyVersion);
        return read(mockMvc.perform(json(post(url(projectId, kind, resource, "en", "translate")), body))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode provenance(UUID projectId) throws Exception {
        return read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/ai-provenance", projectId)))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode warnings(UUID projectId, String code) throws Exception {
        JsonNode readiness = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/readiness", projectId)))
                .andExpect(status().isOk()).andReturn());
        var matching = objectMapper.createArrayNode();
        readiness.get("warnings").forEach(warning -> {
            if (code.equals(warning.get("code").asText())) {
                matching.add(warning);
            }
        });
        return matching;
    }

    private static String url(UUID projectId, String kind, JsonNode resource, String language, String action) {
        return "/api/v1/projects/" + projectId + "/" + kind + "/" + resource.get("id").asText() + "/translations/"
                + language + (action == null ? "" : ":" + action);
    }
}
