package eu.uniche.iguide.settings.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.InitializationFixtures;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code contentRevision} ({@code 03} section 2, {@code 11} section 4; DECISIONS.md D-042): raised by what visitors
 * could see, never by a DRAFT edit or a save into a working copy.
 */
class ContentRevisionIntegrationTest extends InitializationFixtures {

    @Test
    void visibleChangesRaiseTheRevisionAndDraftsAndWorkingCopiesDoNot() throws Exception {
        initializeMuseum(PROJECT_A);
        long start = revision(PROJECT_A);

        JsonNode place = createPlace(PROJECT_A, "Upper Town", "AREA");
        String placeId = place.get("id").asText();
        assertThat(revision(PROJECT_A)).as("a DRAFT Resource").isEqualTo(start);

        JsonNode active = changeStatus(placeUrl(PROJECT_A), placeId, "ACTIVE", place.get("version").asLong(), 200);
        assertThat(revision(PROJECT_A)).as("activation").isEqualTo(start + 1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("slug", active.get("slug").asText());
        payload.put("type", "AREA");
        payload.put("environment", active.get("environment").asText());
        payload.put("featured", true);
        payload.put("translations", Map.of("el", Map.of("title", "Upper Town")));
        saveWorkingCopy(PROJECT_A, "places", placeId, active.get("version").asLong(), null, payload, 200);
        assertThat(revision(PROJECT_A)).as("a save into the working copy").isEqualTo(start + 1);

        JsonNode applied = applyWorkingCopy(PROJECT_A, "places", placeId, 200);
        assertThat(revision(PROJECT_A)).as("apply").isEqualTo(start + 2);

        JsonNode archived = changeStatus(placeUrl(PROJECT_A), placeId, "ARCHIVED", applied.get("version").asLong(), 200);
        assertThat(revision(PROJECT_A)).as("archival of an ACTIVE Resource").isEqualTo(start + 3);
        changeStatus(placeUrl(PROJECT_A), placeId, "DRAFT", archived.get("version").asLong(), 200);
        assertThat(revision(PROJECT_A)).as("restoring to DRAFT").isEqualTo(start + 3);

        mockMvc.perform(json(put(url(PROJECT_A)), "{\"timezone\": \"Europe/Athens\", \"version\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentRevision").value(start + 4))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(json(put(url(PROJECT_A)), "{\"timezone\": \"Mars/Olympus\", \"version\": 1}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(revision(PROJECT_A)).as("a refused settings save rolls back").isEqualTo(start + 4);

        mockMvc.perform(json(put("/api/v1/projects/{p}/languages", PROJECT_A), """
                {"languages": [
                  {"languageCode": "el", "displayName": "Ελληνικά", "direction": "LTR", "isDefault": true},
                  {"languageCode": "en", "displayName": "English", "direction": "LTR", "isDefault": false}
                ]}""")).andExpect(status().isOk());
        assertThat(revision(PROJECT_A)).as("languages").isEqualTo(start + 5);
        // The other Project's revision is its own.
        initializeMuseum(PROJECT_B);
        assertThat(revision(PROJECT_B)).isZero();
    }

    @Test
    void aProjectWithoutSettingsStillChangesStatus() throws Exception {
        languages(PROJECT_A);
        JsonNode place = createPlace(PROJECT_A, "Upper Town", "AREA");
        changeStatus(placeUrl(PROJECT_A), place.get("id").asText(), "ACTIVE", place.get("version").asLong(), 200);
    }

    private long revision(UUID projectId) throws Exception {
        return read(mockMvc.perform(authenticated(get(url(projectId)))).andExpect(status().isOk()).andReturn())
                .get("contentRevision").asLong();
    }

    private static String url(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/settings";
    }
}
