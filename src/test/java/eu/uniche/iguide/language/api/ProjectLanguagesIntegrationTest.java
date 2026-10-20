package eu.uniche.iguide.language.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eu.uniche.iguide.support.ContentIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ProjectLanguagesIntegrationTest extends ContentIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void setsUpTheLanguagesInOrderWithOneDefault() throws Exception {
        languages(PROJECT_A);
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/languages", PROJECT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages[0].languageCode").value("el"))
                .andExpect(jsonPath("$.languages[0].displayName").value("Ελληνικά"))
                .andExpect(jsonPath("$.languages[0].isDefault").value(true))
                .andExpect(jsonPath("$.languages[1].languageCode").value("en"))
                .andExpect(jsonPath("$.languages[1].isDefault").value(false));
    }

    @Test
    void rejectsTwoDefaultsMalformedAndRepeatedCodes() throws Exception {
        mockMvc.perform(json(put("/api/v1/projects/{p}/languages", PROJECT_A), """
                        {"languages": [
                          {"languageCode": "el", "displayName": "Ελληνικά", "direction": "LTR", "isDefault": true},
                          {"languageCode": "el", "displayName": "Again", "direction": "LTR", "isDefault": true},
                          {"languageCode": "Greek!", "displayName": "", "direction": null, "isDefault": false}
                        ]}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.code == 'ONE_DEFAULT_REQUIRED')]").exists())
                .andExpect(jsonPath("$.errors[?(@.path == 'languages[1].languageCode')].code").value("DUPLICATE_VALUE"))
                .andExpect(jsonPath("$.errors[?(@.path == 'languages[2].languageCode')].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[?(@.path == 'languages[2].direction')].code").value("REQUIRED"));
    }

    @Test
    void aRemovedLanguageIsDisabledNotDeleted() throws Exception {
        languages(PROJECT_A);
        mockMvc.perform(json(put("/api/v1/projects/{p}/languages", PROJECT_A), """
                        {"languages": [{"languageCode": "el", "displayName": "Ελληνικά", "direction": "LTR", "isDefault": true}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT enabled FROM project_languages WHERE project_id = ? AND language_code = 'en'",
                Boolean.class, PROJECT_A)).isFalse();
    }

    @Test
    void changingTheDefaultNeedsEveryTitleInTheNewDefault() throws Exception {
        languages(PROJECT_A);
        createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        mockMvc.perform(json(put("/api/v1/projects/{p}/languages", PROJECT_A), """
                        {"languages": [
                          {"languageCode": "el", "displayName": "Ελληνικά", "direction": "LTR", "isDefault": false},
                          {"languageCode": "en", "displayName": "English", "direction": "LTR", "isDefault": true}
                        ]}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("DEFAULT_LANGUAGE_INCOMPLETE"));
    }

    @Test
    void languagesAreProjectScoped() throws Exception {
        languages(PROJECT_A);
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/languages", PROJECT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages.length()").value(0));
    }
}
