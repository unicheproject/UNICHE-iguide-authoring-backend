package eu.uniche.iguide.workingcopy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Working copies end to end against PostgreSQL ({@code 03} section 1, {@code 06} section 17, {@code 07} section
 * 14, {@code 08} Milestone 8), on Places; the other kinds prove their payloads in their own tests.
 */
class WorkingCopyIntegrationTest extends ContentIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    @Test
    void aSaveOnAnActivePlaceNeverChangesTheLiveRow() throws Exception {
        JsonNode place = activePlace(PROJECT_A, "Ακρόπολη");
        long live = place.get("version").asLong();

        JsonNode copy = saveWorkingCopy(PROJECT_A, "places", id(place), live, null, payload("Παρθενώνας", "parthenon"), 200);
        assertThat(copy.get("baseVersion").asLong()).isEqualTo(live);
        assertThat(copy.get("liveVersion").asLong()).isEqualTo(live);
        assertThat(copy.get("updatedBySubject").asText()).isEqualTo("subject-1");
        assertThat(copy.get("payload").get("slug").asText()).isEqualTo("parthenon");
        assertThat(copy.get("resource").get("slug").asText()).isEqualTo("parthenon");
        assertThat(copy.get("resource").get("hasWorkingCopy").asBoolean()).isTrue();

        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A) + "/{id}", id(place))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(place.get("slug").asText()))
                .andExpect(jsonPath("$.translations.el.title").value("Ακρόπολη"))
                .andExpect(jsonPath("$.version").value(live))
                .andExpect(jsonPath("$.hasWorkingCopy").value(true))
                .andExpect(jsonPath("$.workingCopyUpdatedAt").isNotEmpty());
    }

    @Test
    void theEditorReadsTheCopyRenderedAsTheResourceWouldReadAfterApply() throws Exception {
        JsonNode place = activePlace(PROJECT_A, "Ακρόπολη");
        saveWorkingCopy(PROJECT_A, "places", id(place), place.get("version").asLong(), null,
                payload("Παρθενώνας", "parthenon"), 200);

        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "places", id(place)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("PLACE"))
                .andExpect(jsonPath("$.resource.translations.el.title").value("Παρθενώνας"))
                .andExpect(jsonPath("$.errors").isEmpty());

        JsonNode other = activePlace(PROJECT_A, "Δελφοί");
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "places", id(other)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void applyWritesTheCopyAndDeletesIt() throws Exception {
        JsonNode place = activePlace(PROJECT_A, "Ακρόπολη");
        long live = place.get("version").asLong();
        saveWorkingCopy(PROJECT_A, "places", id(place), live, null, payload("Παρθενώνας", "parthenon"), 200);

        JsonNode applied = applyWorkingCopy(PROJECT_A, "places", id(place), 200);
        assertThat(applied.get("slug").asText()).isEqualTo("parthenon");
        assertThat(applied.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(applied.get("version").asLong()).isEqualTo(live + 1);
        assertThat(applied.get("hasWorkingCopy").asBoolean()).isFalse();

        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A) + "/{id}", id(place))))
                .andExpect(jsonPath("$.translations.el.title").value("Παρθενώνας"))
                .andExpect(jsonPath("$.hasWorkingCopy").value(false));
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "places", id(place)))))
                .andExpect(status().isNotFound());
        applyWorkingCopy(PROJECT_A, "places", id(place), 404);
    }

    @Test
    void applyAfterTheLiveRowMovedIsAConflictCarryingTheCurrentVersion() throws Exception {
        JsonNode place = activePlace(PROJECT_A, "Ακρόπολη");
        long live = place.get("version").asLong();
        JsonNode copy = saveWorkingCopy(PROJECT_A, "places", id(place), live, null, payload("Παρθενώνας", "parthenon"), 200);

        // No endpoint moves an ACTIVE row while it has a copy, so the race is staged in the database.
        jdbc.update("UPDATE places SET version = version + 1 WHERE id = ?::uuid", id(place));
        long moved = live + 1;

        JsonNode conflict = applyWorkingCopy(PROJECT_A, "places", id(place), 409);
        assertThat(conflict.get("type").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(conflict.get("currentVersion").asLong()).isEqualTo(moved);

        // Moving the copy onto another version than the live one is refused; onto the live one it is the
        // CMS's "reopen on the current version", after which apply succeeds.
        saveWorkingCopy(PROJECT_A, "places", id(place), live + 5, copy.get("version").asLong(),
                payload("Παρθενώνας", "parthenon"), 409);
        JsonNode rebased = saveWorkingCopy(PROJECT_A, "places", id(place), moved, copy.get("version").asLong(),
                payload("Παρθενώνας", "parthenon"), 200);
        assertThat(rebased.get("baseVersion").asLong()).isEqualTo(moved);
        JsonNode applied = applyWorkingCopy(PROJECT_A, "places", id(place), 200);
        assertThat(applied.get("slug").asText()).isEqualTo("parthenon");
        assertThat(applied.get("version").asLong()).isEqualTo(moved + 1);
    }

    @Test
    void theFirstSaveNeedsTheLiveVersionAndALaterOneTheCopysVersion() throws Exception {
        JsonNode place = activePlace(PROJECT_A, "Ακρόπολη");
        long live = place.get("version").asLong();
        JsonNode stale = saveWorkingCopy(PROJECT_A, "places", id(place), live - 1, null, payload("Α", "a"), 409);
        assertThat(stale.get("currentVersion").asLong()).isEqualTo(live);

        JsonNode copy = saveWorkingCopy(PROJECT_A, "places", id(place), live, null, payload("Α", "a"), 200);
        JsonNode second = saveWorkingCopy(PROJECT_A, "places", id(place), live, copy.get("version").asLong(),
                payload("Β", "b"), 200);
        assertThat(second.get("version").asLong()).isGreaterThan(copy.get("version").asLong());
        assertThat(second.get("createdAt").asText()).isEqualTo(copy.get("createdAt").asText());

        // A save from an editor that loaded the first version lost the race.
        saveWorkingCopy(PROJECT_A, "places", id(place), live, copy.get("version").asLong(), payload("Γ", "c"), 409);
    }

    @Test
    void thePayloadIsValidatedLikeASaveAndNothingIsStoredWhenItFails() throws Exception {
        JsonNode place = activePlace(PROJECT_A, "Ακρόπολη");
        JsonNode other = activePlace(PROJECT_A, "Δελφοί");
        long live = place.get("version").asLong();

        mockMvc.perform(json(put(workingCopyUrl(PROJECT_A, "places", id(place))), """
                        {"baseVersion": %d, "payload": {"slug": "Not A Slug", "type": "SITE",
                         "translations": {"fr": {"title": "x"}}}}""".formatted(live)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.path == 'slug')].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[?(@.path == 'translations.fr')].code").value("UNKNOWN_LANGUAGE"));
        mockMvc.perform(json(put(workingCopyUrl(PROJECT_A, "places", id(place))), """
                        {"baseVersion": %d, "payload": {"slug": "%s", "type": "SITE"}}"""
                        .formatted(live, other.get("slug").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("DUPLICATE_VALUE"));
        mockMvc.perform(json(put(workingCopyUrl(PROJECT_A, "places", id(place))), """
                        {"baseVersion": %d, "payload": {"type": "NOT_A_TYPE"}}""".formatted(live)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("payload"));

        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A) + "/{id}", id(place))))
                .andExpect(jsonPath("$.hasWorkingCopy").value(false))
                .andExpect(jsonPath("$.version").value(live));
    }

    @Test
    void aDraftIsSavedDirectlyAndAnActiveOneOnlyThroughItsCopy() throws Exception {
        JsonNode draft = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        mockMvc.perform(json(put(workingCopyUrl(PROJECT_A, "places", id(draft))), """
                        {"baseVersion": %d, "payload": {"slug": "a", "type": "SITE"}}"""
                        .formatted(draft.get("version").asLong())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_ACTIVE"));

        JsonNode active = activePlace(PROJECT_A, "Δελφοί");
        mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", id(active)), """
                        {"version": %d, "slug": "a", "type": "SITE"}""".formatted(active.get("version").asLong())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_EDITABLE"));
    }

    @Test
    void discardDeletesTheCopyAndNothingElse() throws Exception {
        JsonNode place = activePlace(PROJECT_A, "Ακρόπολη");
        saveWorkingCopy(PROJECT_A, "places", id(place), place.get("version").asLong(), null, payload("Α", "a"), 200);

        mockMvc.perform(authenticated(delete(workingCopyUrl(PROJECT_A, "places", id(place)))))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A) + "/{id}", id(place))))
                .andExpect(jsonPath("$.translations.el.title").value("Ακρόπολη"))
                .andExpect(jsonPath("$.version").value(place.get("version").asLong()))
                .andExpect(jsonPath("$.hasWorkingCopy").value(false));
        mockMvc.perform(authenticated(delete(workingCopyUrl(PROJECT_A, "places", id(place)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatingOrArchivingDiscardsTheCopy() throws Exception {
        JsonNode place = activePlace(PROJECT_A, "Ακρόπολη");
        saveWorkingCopy(PROJECT_A, "places", id(place), place.get("version").asLong(), null, payload("Α", "a"), 200);
        JsonNode draft = changeStatus(placeUrl(PROJECT_A), id(place), "DRAFT", place.get("version").asLong(), 200);
        assertThat(draft.get("hasWorkingCopy").asBoolean()).isFalse();
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "places", id(place)))))
                .andExpect(status().isNotFound());

        JsonNode active = changeStatus(placeUrl(PROJECT_A), id(place), "ACTIVE", draft.get("version").asLong(), 200);
        saveWorkingCopy(PROJECT_A, "places", id(place), active.get("version").asLong(), null, payload("Α", "a"), 200);
        changeStatus(placeUrl(PROJECT_A), id(place), "ARCHIVED", active.get("version").asLong(), 200);
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "places", id(place)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void theListFiltersPendingResourcesAndFlagsThem() throws Exception {
        JsonNode pending = activePlace(PROJECT_A, "Ακρόπολη");
        activePlace(PROJECT_A, "Δελφοί");
        saveWorkingCopy(PROJECT_A, "places", id(pending), pending.get("version").asLong(), null, payload("Α", "a"), 200);

        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A)).param("pending", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id(pending)))
                .andExpect(jsonPath("$.content[0].hasWorkingCopy").value(true));
        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A))))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.hasWorkingCopy == true)].id").value(id(pending)));
    }

    @Test
    void anotherProjectsResourceAndCopyAreNotFound() throws Exception {
        JsonNode place = activePlace(PROJECT_A, "Ακρόπολη");
        JsonNode foreignParent = createPlace(PROJECT_B, "Κνωσός", "SITE");
        long live = place.get("version").asLong();

        saveWorkingCopy(PROJECT_B, "places", id(place), live, null, payload("Α", "a"), 404);
        mockMvc.perform(json(put(workingCopyUrl(PROJECT_A, "places", id(place))), """
                        {"baseVersion": %d, "payload": {"slug": "a", "type": "SITE", "parentPlaceId": "%s"}}"""
                        .formatted(live, id(foreignParent))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.path == 'parentPlaceId')].code").value("RESOURCE_NOT_FOUND"));

        saveWorkingCopy(PROJECT_A, "places", id(place), live, null, payload("Α", "a"), 200);
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_B, "places", id(place)))))
                .andExpect(status().isNotFound());
        applyWorkingCopy(PROJECT_B, "places", id(place), 404);
        mockMvc.perform(authenticated(delete(workingCopyUrl(PROJECT_B, "places", id(place)))))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get(placeUrl(PROJECT_B)).param("pending", "true")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anUnknownKindIsNotFound() throws Exception {
        mockMvc.perform(authenticated(get(workingCopyUrl(PROJECT_A, "assets", UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCallWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(put(workingCopyUrl(PROJECT_A, "places", UUID.randomUUID()))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode activePlace(UUID projectId, String title) throws Exception {
        JsonNode place = createPlace(projectId, title, "SITE");
        return changeStatus(placeUrl(projectId), id(place), "ACTIVE", place.get("version").asLong(), 200);
    }

    private static String payload(String title, String slug) {
        return """
                {"slug": "%s", "type": "SITE", "featured": true,
                 "translations": {"el": {"title": "%s"}}}""".formatted(slug, title);
    }

    private static String id(JsonNode resource) {
        return resource.get("id").asText();
    }
}
