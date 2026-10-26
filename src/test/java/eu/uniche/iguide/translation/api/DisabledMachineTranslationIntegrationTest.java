package eu.uniche.iguide.translation.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** The default gateway mode: no AI gateway, so machine translation is {@code 503} (DECISIONS.md D-035). */
@TestPropertySource(properties = "iguide.ai.gateway.mode=disabled")
class DisabledMachineTranslationIntegrationTest extends ContentIntegrationTest {

    @Test
    void translationIsUnavailable() throws Exception {
        languages(PROJECT_A);
        mockMvc.perform(authenticated(get("/api/v1/ai/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translation").value("DISABLED"));

        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        mockMvc.perform(json(post("/api/v1/projects/{p}/places/{id}/translations/en:translate", PROJECT_A,
                        place.get("id").asText()), Map.of("version", place.get("version").asLong())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AI_GATEWAY_UNAVAILABLE"))
                .andExpect(jsonPath("$.type").value("urn:uniche:iguide:problem:AI_GATEWAY_UNAVAILABLE"));
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/places/{id}", PROJECT_A, place.get("id").asText())))
                .andExpect(jsonPath("$.translations.en").doesNotExist());
    }
}
