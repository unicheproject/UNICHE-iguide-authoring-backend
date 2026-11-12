package eu.uniche.iguide.settings.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eu.uniche.iguide.support.InitializationFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET}, {@code PUT .../settings} ({@code 03} section 2; DECISIONS.md D-040): {@code featureConfiguration}
 * takes only the applied Template's capabilities.
 */
class ProjectSettingsIntegrationTest extends InitializationFixtures {

    @Test
    void noSettingsBeforeInitialization() throws Exception {
        mockMvc.perform(authenticated(get(url(PROJECT_A))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_INITIALIZED"));
    }

    @Test
    void aFeatureFlagOutsideTheTemplatesCapabilitiesIsRejected() throws Exception {
        initializeMuseum(PROJECT_A);
        mockMvc.perform(json(put(url(PROJECT_A)), """
                        {"featureConfiguration": {"LOCATION_TRIGGERS": true, "EVENTS": false}, "version": 0}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].path").value("featureConfiguration.LOCATION_TRIGGERS"))
                .andExpect(jsonPath("$.errors[0].code").value("CAPABILITY_NOT_IN_TEMPLATE"));
        mockMvc.perform(authenticated(get(url(PROJECT_A))))
                .andExpect(jsonPath("$.featureConfiguration.EVENTS").value(true));
    }

    @Test
    void setsTheListedCapabilitiesAndTheTimezone() throws Exception {
        initializeMuseum(PROJECT_A);
        mockMvc.perform(json(put(url(PROJECT_A)), """
                        {"featureConfiguration": {"EVENTS": false}, "timezone": "Europe/Athens", "version": 0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureConfiguration.EVENTS").value(false))
                .andExpect(jsonPath("$.featureConfiguration.ROUTES").value(true))
                .andExpect(jsonPath("$.timezone").value("Europe/Athens"))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(json(put(url(PROJECT_A)), "{\"timezone\": \"Mars/Olympus\", \"version\": 1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("timezone"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TIMEZONE"));
        mockMvc.perform(json(put(url(PROJECT_A)), "{\"timezone\": null, \"version\": 0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mockMvc.perform(authenticated(get(url(PROJECT_B))))
                .andExpect(status().isConflict());
    }

    private static String url(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/settings";
    }
}
