package eu.uniche.iguide.place.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Places end to end against PostgreSQL ({@code 03} section 5, {@code 08} Milestone 4). */
class PlaceIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    // --- Create and read -------------------------------------------------------------------------

    @Test
    void createsADraftWithItsTitleAGeneratedSlugAndAnEnvironmentFromItsType() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Museum of Cycladic Art", "BUILDING");
        assertThat(place.get("status").asText()).isEqualTo("DRAFT");
        assertThat(place.get("slug").asText()).isEqualTo("museum-of-cycladic-art");
        assertThat(place.get("environment").asText()).isEqualTo("INDOOR");
        assertThat(place.get("translations").get("el").get("title").asText()).isEqualTo("Museum of Cycladic Art");
        assertThat(place.get("hasWorkingCopy").asBoolean()).isFalse();

        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A) + "/{id}", place.get("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("museum-of-cycladic-art"));
    }

    @Test
    void aGeneratedSlugAvoidsOneAlreadyTakenAndAccentsAreDropped() throws Exception {
        createPlace(PROJECT_A, "Café Square", "AREA");
        JsonNode second = createPlace(PROJECT_A, "Café Square", "AREA");
        assertThat(second.get("slug").asText()).isEqualTo("cafe-square-2");
    }

    @Test
    void creationNeedsATitleATypeAndADefaultLanguage() throws Exception {
        mockMvc.perform(json(post(placeUrl(PROJECT_A)), "{\"title\": \" \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.path == 'title')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.path == 'type')].code").value("REQUIRED"));

        clean();
        admitBothProjects();
        mockMvc.perform(json(post(placeUrl(PROJECT_A)), "{\"title\": \"Delos\", \"type\": \"SITE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("NO_PROJECT_LANGUAGES"));
    }

    @Test
    void aSlugIsUniquePerProjectButNotAcrossProjects() throws Exception {
        createPlace(PROJECT_A, "{\"title\": \"Delos\", \"type\": \"SITE\", \"slug\": \"delos\"}");
        mockMvc.perform(json(post(placeUrl(PROJECT_A)), "{\"title\": \"Delos\", \"type\": \"SITE\", \"slug\": \"delos\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("DUPLICATE_VALUE"))
                .andExpect(jsonPath("$.errors[0].path").value("slug"));
        createPlace(PROJECT_B, "{\"title\": \"Delos\", \"type\": \"SITE\", \"slug\": \"delos\"}");
    }

    @Test
    void aMalformedSlugIsRejected() throws Exception {
        mockMvc.perform(json(post(placeUrl(PROJECT_A)), "{\"title\": \"Delos\", \"type\": \"SITE\", \"slug\": \"Not A Slug\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    // --- Update ----------------------------------------------------------------------------------

    @Test
    void savesADraftWithTranslationsAndBumpsTheVersion() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        JsonNode saved = update(place, Map.of("translations", Map.of(
                "el", Map.of("title", "Ακρόπολη", "summary", "Ο ιερός βράχος"),
                "en", Map.of("title", "Acropolis", "accessibilityInformation", "Lift on the north slope"))), 200);
        assertThat(saved.get("version").asLong()).isGreaterThan(place.get("version").asLong());
        assertThat(saved.get("translations").get("en").get("title").asText()).isEqualTo("Acropolis");
        assertThat(saved.get("translations").get("el").get("summary").asText()).isEqualTo("Ο ιερός βράχος");
        assertThat(saved.get("latitude").decimalValue()).isEqualByComparingTo("37.971500");
    }

    @Test
    void aStaleVersionIsAConflictCarryingTheCurrentVersion() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        update(place, Map.of(), 200);
        mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", place.get("id").asText()),
                        body(place, Map.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(place.get("version").asLong() + 1));
    }

    @Test
    void rejectsAnUnknownLanguageAndHalfACoordinatePair() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        Map<String, Object> changes = new java.util.HashMap<>();
        changes.put("translations", Map.of("fr", Map.of("title", "Acropole")));
        changes.put("longitude", null);
        mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", place.get("id").asText()), body(place, changes)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.path == 'translations.fr')].code").value("UNKNOWN_LANGUAGE"))
                .andExpect(jsonPath("$.errors[?(@.path == 'latitude')].code").value("INCOMPLETE"));
    }

    @Test
    void anActivePlaceIsNotEditedInPlace() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        JsonNode active = changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ACTIVE",
                place.get("version").asLong(), 200);
        mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", active.get("id").asText()), body(active, Map.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
    }

    // --- Hierarchy -------------------------------------------------------------------------------

    @Test
    void theHierarchyRejectsItselfACycleAndAnArchivedParent() throws Exception {
        JsonNode site = createPlace(PROJECT_A, "Acropolis", "SITE");
        JsonNode building = createPlace(PROJECT_A, "{\"title\": \"Parthenon\", \"type\": \"BUILDING\", \"parentPlaceId\": \""
                + site.get("id").asText() + "\"}");
        assertThat(building.get("parentPlaceId").asText()).isEqualTo(site.get("id").asText());

        mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", site.get("id").asText()),
                        body(site, Map.of("parentPlaceId", site.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_REFERENCE"));
        mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", site.get("id").asText()),
                        body(site, Map.of("parentPlaceId", building.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("CYCLE"));

        JsonNode archived = createPlace(PROJECT_A, "Old wing", "AREA");
        changeStatus(placeUrl(PROJECT_A), archived.get("id").asText(), "ARCHIVED", archived.get("version").asLong(), 200);
        mockMvc.perform(json(post(placeUrl(PROJECT_A)), "{\"title\": \"Room\", \"type\": \"ROOM\", \"parentPlaceId\": \""
                        + archived.get("id").asText() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("ARCHIVED_REFERENCE"));
    }

    // --- Lifecycle -------------------------------------------------------------------------------

    @Test
    void activationNeedsTheTitleInTheDefaultLanguage() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        JsonNode untitled = update(place, Map.of("translations", Map.of("el", Map.of("title", ""),
                "en", Map.of("title", "Acropolis"))), 200);
        JsonNode error = changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ACTIVE",
                untitled.get("version").asLong(), 422);
        assertThat(error.get("errors").get(0).get("code").asText()).isEqualTo("MISSING_REQUIRED_FIELDS");
        assertThat(error.get("errors").get(0).get("path").asText()).isEqualTo("translations.el.title");
    }

    @Test
    void archivingThenRestoringIsTheOnlyWayOutOfArchived() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        JsonNode archived = changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ARCHIVED",
                place.get("version").asLong(), 200);
        assertThat(archived.get("archivedAt").isNull()).isFalse();

        JsonNode illegal = changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ACTIVE",
                archived.get("version").asLong(), 422);
        assertThat(illegal.get("errors").get(0).get("code").asText()).isEqualTo("ILLEGAL_TRANSITION");

        JsonNode restored = changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "DRAFT",
                archived.get("version").asLong(), 200);
        assertThat(restored.get("status").asText()).isEqualTo("DRAFT");
        assertThat(restored.get("archivedAt").isNull()).isTrue();
    }

    // --- Listing and order -----------------------------------------------------------------------

    @Test
    void listsInSortOrderPagesFiltersAndSearchesTitlesInAnyLanguage() throws Exception {
        JsonNode first = createPlace(PROJECT_A, "Acropolis", "SITE");
        JsonNode second = createPlace(PROJECT_A, "Delos", "SITE");
        JsonNode third = createPlace(PROJECT_A, "Knossos", "SITE");
        update(third, Map.of("translations", Map.of("en", Map.of("title", "Palace of Minos"))), 200);

        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A)).param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first.get("id").asText()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A)).param("q", "minos")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(third.get("id").asText()));

        changeStatus(placeUrl(PROJECT_A), second.get("id").asText(), "ARCHIVED", second.get("version").asLong(), 200);
        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A)).param("status", "ARCHIVED")))
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A)).param("size", "201")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reorderingNeedsTheCompleteListAndSetsTheOrder() throws Exception {
        JsonNode first = createPlace(PROJECT_A, "Acropolis", "SITE");
        JsonNode second = createPlace(PROJECT_A, "Delos", "SITE");
        JsonNode foreign = createPlace(PROJECT_B, "Elsewhere", "SITE");

        mockMvc.perform(json(put(placeUrl(PROJECT_A) + ":order"),
                        Map.of("ids", java.util.List.of(second.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(json(put(placeUrl(PROJECT_A) + ":order"), Map.of("ids", java.util.List.of(
                        second.get("id").asText(), foreign.get("id").asText()))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(json(put(placeUrl(PROJECT_A) + ":order"), Map.of("ids", java.util.List.of(
                        second.get("id").asText(), first.get("id").asText()))))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A))))
                .andExpect(jsonPath("$.content[0].id").value(second.get("id").asText()));
    }

    // --- Cross-Project isolation -----------------------------------------------------------------

    @Test
    void anotherProjectsPlaceIsNotFoundInThePathOrAsAParent() throws Exception {
        JsonNode foreign = createPlace(PROJECT_B, "Elsewhere", "SITE");
        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A) + "/{id}", foreign.get("id").asText())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(json(post(placeUrl(PROJECT_A)), "{\"title\": \"Room\", \"type\": \"ROOM\", \"parentPlaceId\": \""
                        + foreign.get("id").asText() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A))))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anUnknownPathIsNotFoundRatherThanAServerError() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/no-such-kind", PROJECT_A)))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode update(JsonNode place, Map<String, Object> changes, int expected) throws Exception {
        return read(mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", place.get("id").asText()),
                body(place, changes))).andExpect(status().is(expected)).andReturn());
    }

    /** The place as read, with coordinates set, and the given fields replaced. */
    private Map<String, Object> body(JsonNode place, Map<String, Object> changes) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("version", place.get("version").asLong());
        body.put("slug", place.get("slug").asText());
        body.put("type", place.get("type").asText());
        body.put("environment", place.get("environment").asText());
        body.put("parentPlaceId", place.get("parentPlaceId").isNull() ? null : place.get("parentPlaceId").asText());
        body.put("latitude", 37.9715);
        body.put("longitude", 23.7257);
        body.put("featured", false);
        body.putAll(changes);
        return body;
    }
}
