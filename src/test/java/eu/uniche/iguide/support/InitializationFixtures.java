package eu.uniche.iguide.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.template.application.TemplateSeeder;
import java.util.UUID;

/** Initialization helpers over {@link ContentIntegrationTest}: the seeded Templates and the initialize call. */
public abstract class InitializationFixtures extends ContentIntegrationTest {

    protected static final UUID MUSEUM = TemplateSeeder.templateId("museum-multi-surface", 1);
    protected static final UUID OUTDOOR = TemplateSeeder.templateId("outdoor-trail", 1);
    protected static final UUID NETWORK = TemplateSeeder.templateId("multi-region-network", 1);

    protected static String initializeUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/initialize";
    }

    protected JsonNode initialize(UUID projectId, String body, int expected) throws Exception {
        return read(mockMvc.perform(json(post(initializeUrl(projectId)), body))
                .andExpect(status().is(expected)).andReturn());
    }

    /** The museum Template with Greek default, English second, WEB and KIOSK, the suggested style. */
    protected JsonNode initializeMuseum(UUID projectId) throws Exception {
        return initialize(projectId, """
                {"templateId": "%s", "stylePreset": "gallery-house", "languages": ["el", "en"],
                 "defaultLanguage": "el", "surfaces": ["WEB", "KIOSK"]}""".formatted(MUSEUM), 200);
    }
}
