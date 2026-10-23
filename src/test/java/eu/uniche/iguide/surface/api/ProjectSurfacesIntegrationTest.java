package eu.uniche.iguide.surface.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The surfaces of a Project and the WEB surface's public web address ({@code 03} section 2, D-029). */
class ProjectSurfacesIntegrationTest extends ContentIntegrationTest {

    @Test
    void everySurfaceReadsDisabledUntilSaved() throws Exception {
        mockMvc.perform(authenticated(get(url(PROJECT_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surfaces.length()").value(3))
                .andExpect(jsonPath("$.surfaces[0].surface").value("WEB"))
                .andExpect(jsonPath("$.surfaces[1].surface").value("MOBILE"))
                .andExpect(jsonPath("$.surfaces[2].surface").value("KIOSK"))
                .andExpect(jsonPath("$.surfaces[0].enabled").value(false))
                .andExpect(jsonPath("$.surfaces[0].configuration").isEmpty());
    }

    @Test
    void savesTheSurfacesAndNormalizesTheAddress() throws Exception {
        mockMvc.perform(json(put(url(PROJECT_A)), """
                        {"surfaces": [
                          {"surface": "WEB", "enabled": true, "configuration": {"publicBaseUrl": " HTTPS://Guide.Museum.gr/ "}},
                          {"surface": "KIOSK", "enabled": true, "configuration": {}}
                        ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surfaces[0].enabled").value(true))
                .andExpect(jsonPath("$.surfaces[0].configuration.publicBaseUrl").value("https://guide.museum.gr"))
                .andExpect(jsonPath("$.surfaces[1].enabled").value(false))
                .andExpect(jsonPath("$.surfaces[2].enabled").value(true));

        // A surface left out keeps its row; a blank address removes it.
        mockMvc.perform(json(put(url(PROJECT_A)), """
                        {"surfaces": [{"surface": "WEB", "enabled": true, "configuration": {"publicBaseUrl": " "}}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surfaces[0].configuration").isEmpty())
                .andExpect(jsonPath("$.surfaces[2].enabled").value(true));

        mockMvc.perform(json(put(url(PROJECT_A)), """
                        {"surfaces": [{"surface": "WEB", "enabled": true,
                                       "configuration": {"publicBaseUrl": "http://localhost:5173"}}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surfaces[0].configuration.publicBaseUrl").value("http://localhost:5173"));
    }

    @Test
    void theAddressIsAnHttpsOriginOrLocalHttp() throws Exception {
        for (String bad : new String[] {"http://guide.museum.gr", "https://guide.museum.gr/app", "guide.museum.gr",
                "https://guide.museum.gr?x=1", "https://user@guide.museum.gr", "ftp://guide.museum.gr", "https://"}) {
            mockMvc.perform(json(put(url(PROJECT_A)), """
                            {"surfaces": [{"surface": "WEB", "enabled": true, "configuration": {"publicBaseUrl": "%s"}}]}"""
                            .formatted(bad)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].path").value("surfaces[0].configuration.publicBaseUrl"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_URL"));
        }
    }

    @Test
    void unknownKeysAndRepeatedSurfacesAreRefused() throws Exception {
        mockMvc.perform(json(put(url(PROJECT_A)), """
                        {"surfaces": [
                          {"surface": "MOBILE", "enabled": true, "configuration": {"publicBaseUrl": "https://a.gr"}},
                          {"surface": "WEB", "enabled": true, "configuration": {"theme": "dark"}},
                          {"surface": "WEB", "enabled": false}
                        ]}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.path == 'surfaces[0].configuration.publicBaseUrl')].code").value("UNKNOWN_KEY"))
                .andExpect(jsonPath("$.errors[?(@.path == 'surfaces[1].configuration.theme')].code").value("UNKNOWN_KEY"))
                .andExpect(jsonPath("$.errors[?(@.path == 'surfaces[2].surface')].code").value("DUPLICATE_VALUE"));
        mockMvc.perform(authenticated(get(url(PROJECT_A))))
                .andExpect(jsonPath("$.surfaces[0].enabled").value(false));
    }

    @Test
    void surfacesBelongToTheirProject() throws Exception {
        mockMvc.perform(json(put(url(PROJECT_A)), """
                        {"surfaces": [{"surface": "WEB", "enabled": true,
                                       "configuration": {"publicBaseUrl": "https://a.museum.gr"}}]}"""))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(get(url(PROJECT_B))))
                .andExpect(jsonPath("$.surfaces[0].enabled").value(false))
                .andExpect(jsonPath("$.surfaces[0].configuration").isEmpty());
    }

    private static String url(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/surfaces";
    }
}
