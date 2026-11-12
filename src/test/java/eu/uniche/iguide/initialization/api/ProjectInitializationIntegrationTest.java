package eu.uniche.iguide.initialization.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.platform.catalogue.CatalogueNotFoundException;
import eu.uniche.iguide.support.InitializationFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code POST .../initialize} ({@code 04} section 8, {@code 07} section 8, {@code 08} Milestone 2; DECISIONS.md
 * D-040): an ACTIVE Template initializes an empty Project, repeats are refused, a failed attempt leaves nothing and
 * can be re-run, defaults are copies, and every choice stays inside the Template.
 */
class ProjectInitializationIntegrationTest extends InitializationFixtures {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anActiveTemplateInitializesAnEmptyProject() throws Exception {
        JsonNode project = initializeMuseum(PROJECT_A);
        assertThat(project.get("initialized").asBoolean()).isTrue();
        assertThat(project.get("initializedAt").isNull()).isFalse();
        assertThat(project.at("/template/id").asText()).isEqualTo(MUSEUM.toString());
        assertThat(project.at("/template/key").asText()).isEqualTo("museum-multi-surface");
        assertThat(project.at("/template/version").asInt()).isEqualTo(1);
        assertThat(project.at("/template/name").asText()).isEqualTo("Museum multi-surface");
        assertThat(project.get("stylePresetKey").asText()).isEqualTo("gallery-house");
        assertThat(project.get("applicationStatus").asText()).isEqualTo("DRAFT");
        assertThat(project.get("enabledSurfaces")).hasSize(2);

        // GET /projects/{id} reports the same, the template name in the caller's language.
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}", PROJECT_A)).header("Accept-Language", "el"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(true))
                .andExpect(jsonPath("$.template.name").value("Μουσείο πολλαπλών επιφανειών"))
                .andExpect(jsonPath("$.stylePresetKey").value("gallery-house"))
                .andExpect(jsonPath("$.enabledSurfaces[0]").value("WEB"))
                .andExpect(jsonPath("$.enabledSurfaces[1]").value("KIOSK"));

        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/languages", PROJECT_A)))
                .andExpect(jsonPath("$.languages.length()").value(2))
                .andExpect(jsonPath("$.languages[0].languageCode").value("el"))
                .andExpect(jsonPath("$.languages[0].displayName").value("Ελληνικά"))
                .andExpect(jsonPath("$.languages[0].isDefault").value(true))
                .andExpect(jsonPath("$.languages[1].languageCode").value("en"));
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/surfaces", PROJECT_A)))
                .andExpect(jsonPath("$.surfaces[0].enabled").value(true))
                .andExpect(jsonPath("$.surfaces[1].enabled").value(false))
                .andExpect(jsonPath("$.surfaces[2].enabled").value(true));
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/theme", PROJECT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stylePresetKey").value("gallery-house"))
                .andExpect(jsonPath("$.stylePresetModified").value(false))
                .andExpect(jsonPath("$.headingFont").value("SPECTRAL"))
                .andExpect(jsonPath("$.primaryColor").value("#4C1D8B"))
                .andExpect(jsonPath("$.shell.languageSwitcher").value("HEADER"))
                .andExpect(jsonPath("$.shell.kioskHome").value("TILES"))
                .andExpect(jsonPath("$.contrast.passes").value(true));
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/settings", PROJECT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.featureConfiguration.FLOOR_PLANS").value(true))
                .andExpect(jsonPath("$.featureConfiguration.LOCATION_TRIGGERS").doesNotExist())
                .andExpect(jsonPath("$.capabilities.length()").value(11));

        // The Template's initial Categories, DRAFT, named in the enabled languages; and the provenance row.
        List<Map<String, Object>> categories = jdbc.queryForList("""
                SELECT c.code, c.status, c.scope, t.language_code, t.name FROM categories c
                JOIN category_translations t ON t.category_id = c.id
                WHERE c.project_id = ? ORDER BY c.sort_order, t.language_code""", PROJECT_A);
        assertThat(categories).extracting(row -> row.get("code") + ":" + row.get("language_code"))
                .containsExactly("highlights:el", "highlights:en", "family-route:el", "family-route:en");
        assertThat(categories).allMatch(row -> "DRAFT".equals(row.get("status")));
        Map<String, Object> application = jdbc.queryForMap("""
                SELECT template_id, template_key_snapshot, template_version_snapshot, applied_by_subject,
                       original_template_snapshot->>'key' AS snapshot_key
                FROM project_template_applications WHERE project_id = ?""", PROJECT_A);
        assertThat(application.get("template_id")).isEqualTo(MUSEUM);
        assertThat(application.get("template_key_snapshot")).isEqualTo("museum-multi-surface");
        assertThat(application.get("applied_by_subject")).isEqualTo("subject-1");
        assertThat(application.get("snapshot_key")).isEqualTo("museum-multi-surface");
    }

    @Test
    void initializationOfAnInitializedProjectIsRejected() throws Exception {
        initializeMuseum(PROJECT_A);
        mockMvc.perform(json(post(initializeUrl(PROJECT_A)), "{\"templateId\": \"" + OUTDOOR + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PROJECT_ALREADY_INITIALIZED"))
                .andExpect(jsonPath("$.type").value("urn:uniche:iguide:problem:PROJECT_ALREADY_INITIALIZED"));
        assertThat(count("project_template_applications", PROJECT_A)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categories WHERE project_id = ?", Long.class, PROJECT_A))
                .isEqualTo(2);
    }

    @Test
    void theTemplateDefaultsApplyWhenTheRequestNamesNone() throws Exception {
        JsonNode project = initialize(PROJECT_A, "{\"templateId\": \"" + OUTDOOR + "\"}", 200);
        assertThat(project.get("stylePresetKey").asText()).isEqualTo("quarry-trail");
        assertThat(project.get("enabledSurfaces").toString()).isEqualTo("[\"WEB\",\"MOBILE\"]");
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/languages", PROJECT_A)))
                .andExpect(jsonPath("$.languages[0].languageCode").value("el"))
                .andExpect(jsonPath("$.languages[0].isDefault").value(true))
                .andExpect(jsonPath("$.languages[1].languageCode").value("en"));
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/settings", PROJECT_A)))
                .andExpect(jsonPath("$.featureConfiguration.LOCATION_TRIGGERS").value(true))
                .andExpect(jsonPath("$.featureConfiguration.FLOOR_PLANS").doesNotExist());
    }

    @Test
    void languagesCarryTheirNameAndDirectionAndADeclaredCapabilityCanStartDisabled() throws Exception {
        initialize(PROJECT_A, """
                {"templateId": "%s", "languages": ["en", {"languageCode": "ar"},
                   {"languageCode": "tlh", "displayName": "Klingon", "direction": "LTR"}],
                 "defaultLanguage": "en", "surfaces": ["WEB"], "featureConfiguration": {"EVENTS": false}}"""
                .formatted(NETWORK), 200);
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/languages", PROJECT_A)))
                .andExpect(jsonPath("$.languages[0].isDefault").value(true))
                .andExpect(jsonPath("$.languages[1].displayName").value("العربية"))
                .andExpect(jsonPath("$.languages[1].direction").value("RTL"))
                .andExpect(jsonPath("$.languages[2].displayName").value("Klingon"));
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/settings", PROJECT_A)))
                .andExpect(jsonPath("$.featureConfiguration.EVENTS").value(false))
                .andExpect(jsonPath("$.featureConfiguration.ROUTES").value(true));
    }

    @Test
    void choicesOutsideTheTemplateAreRejectedWithTheirPaths() throws Exception {
        JsonNode problem = initialize(PROJECT_A, """
                {"templateId": "%s", "stylePreset": "no-such-style", "languages": ["el", "zz", "el"],
                 "defaultLanguage": "fr", "surfaces": ["WEB", "MOBILE"],
                 "featureConfiguration": {"LOCATION_TRIGGERS": true, "TELEPORT": true}}""".formatted(MUSEUM), 422);
        assertThat(problem.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(codes(problem)).contains(
                "stylePreset:STYLE_PRESET_NOT_OFFERED",
                "defaultLanguage:DEFAULT_LANGUAGE_NOT_LISTED",
                "languages[1].displayName:REQUIRED",
                "languages[1].direction:REQUIRED",
                "languages[2].languageCode:DUPLICATE_VALUE",
                "surfaces[1]:SURFACE_NOT_SUPPORTED",
                "featureConfiguration.LOCATION_TRIGGERS:CAPABILITY_NOT_IN_TEMPLATE",
                "featureConfiguration.TELEPORT:UNKNOWN_CAPABILITY");
        assertThat(problem.toString()).doesNotContain("no-such-style");

        assertThat(codes(initialize(PROJECT_A, "{}", 422))).containsExactly("templateId:REQUIRED");
        assertThat(codes(initialize(PROJECT_A, "{\"templateId\": \"" + UUID.randomUUID() + "\"}", 422)))
                .containsExactly("templateId:TEMPLATE_NOT_FOUND");
        assertThat(codes(initialize(PROJECT_A, "{\"templateId\": \"" + MUSEUM + "\", \"surfaces\": []}", 422)))
                .containsExactly("surfaces:REQUIRED");
        // Nothing was written by any of them.
        assertThat(count("project_settings", PROJECT_A) + count("project_themes", PROJECT_A)
                + count("project_template_applications", PROJECT_A)).isZero();
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}", PROJECT_A)))
                .andExpect(jsonPath("$.initialized").value(false))
                .andExpect(jsonPath("$.initializedAt").isEmpty())
                .andExpect(jsonPath("$.template").isEmpty());
    }

    @Test
    void onlyAnActiveTemplateVersionInitializes() throws Exception {
        jdbc.update("UPDATE application_templates SET status = 'DEPRECATED' WHERE id = ?", OUTDOOR);
        try {
            assertThat(codes(initialize(PROJECT_A, "{\"templateId\": \"" + OUTDOOR + "\"}", 422)))
                    .containsExactly("templateId:TEMPLATE_NOT_ACTIVE");
        } finally {
            jdbc.update("UPDATE application_templates SET status = 'ACTIVE' WHERE id = ?", OUTDOOR);
        }
    }

    @Test
    void aFailedInitializationLeavesNothingAndCanBeRerunWithoutDuplicates() throws Exception {
        // Set up in App settings before initialization, as the CMS does: languages, surfaces, a Category, a Place.
        languages(PROJECT_A);
        mockMvc.perform(json(put("/api/v1/projects/{p}/surfaces", PROJECT_A), """
                        {"surfaces": [{"surface": "WEB", "enabled": true,
                                       "configuration": {"publicBaseUrl": "https://guide.museum.gr"}},
                                      {"surface": "MOBILE", "enabled": true}]}"""))
                .andExpect(status().isOk());
        mockMvc.perform(json(post("/api/v1/projects/{p}/categories", PROJECT_A),
                        "{\"name\": \"Our highlights\", \"code\": \"highlights\", \"scope\": \"POI\"}"))
                .andExpect(status().isCreated());
        createPlace(PROJECT_A, "Αρχαία αγορά", "SITE");

        // Fails inside the transaction, after settings and theme were written: the Place has no English title.
        JsonNode failed = initialize(PROJECT_A, """
                {"templateId": "%s", "languages": ["el", "en"], "defaultLanguage": "en",
                 "surfaces": ["WEB", "KIOSK"]}""".formatted(MUSEUM), 422);
        assertThat(codes(failed)).containsExactly("languages:DEFAULT_LANGUAGE_INCOMPLETE");
        assertThat(count("project_settings", PROJECT_A) + count("project_themes", PROJECT_A)
                + count("project_template_applications", PROJECT_A)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categories WHERE project_id = ?", Long.class, PROJECT_A))
                .isEqualTo(1);

        // Resumed: the same request with the Greek default succeeds and duplicates nothing.
        initialize(PROJECT_A, """
                {"templateId": "%s", "languages": ["el", "en"], "defaultLanguage": "el",
                 "surfaces": ["WEB", "KIOSK"]}""".formatted(MUSEUM), 200);
        assertThat(jdbc.queryForList("SELECT code FROM categories WHERE project_id = ? ORDER BY sort_order",
                String.class, PROJECT_A)).containsExactly("highlights", "family-route");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_languages WHERE project_id = ?", Long.class,
                PROJECT_A)).isEqualTo(2);
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/surfaces", PROJECT_A)))
                .andExpect(jsonPath("$.surfaces[0].enabled").value(true))
                .andExpect(jsonPath("$.surfaces[0].configuration.publicBaseUrl").value("https://guide.museum.gr"))
                .andExpect(jsonPath("$.surfaces[1].enabled").value(false))
                .andExpect(jsonPath("$.surfaces[2].enabled").value(true));
    }

    @Test
    void afterInitializationASurfaceTheTemplateDoesNotSupportCannotBeEnabled() throws Exception {
        initializeMuseum(PROJECT_A);
        mockMvc.perform(json(put("/api/v1/projects/{p}/surfaces", PROJECT_A),
                        "{\"surfaces\": [{\"surface\": \"MOBILE\", \"enabled\": true}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("surfaces[0].surface"))
                .andExpect(jsonPath("$.errors[0].code").value("SURFACE_NOT_SUPPORTED"));
        mockMvc.perform(json(put("/api/v1/projects/{p}/surfaces", PROJECT_A),
                        "{\"surfaces\": [{\"surface\": \"KIOSK\", \"enabled\": false}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surfaces[2].enabled").value(false));
        // Languages stay editable after initialization, surfaces and languages before it (the CMS App settings).
        mockMvc.perform(json(put("/api/v1/projects/{p}/languages", PROJECT_A), """
                        {"languages": [{"languageCode": "el", "displayName": "Ελληνικά", "direction": "LTR", "isDefault": true}]}"""))
                .andExpect(status().isOk());
        mockMvc.perform(json(put("/api/v1/projects/{p}/surfaces", PROJECT_B),
                        "{\"surfaces\": [{\"surface\": \"MOBILE\", \"enabled\": true}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void templateDefaultsAreCopiedNotLinked() throws Exception {
        initializeMuseum(PROJECT_A);
        String original = jdbc.queryForObject("SELECT definition::text FROM application_templates WHERE id = ?",
                String.class, MUSEUM);
        try {
            jdbc.update("""
                    UPDATE application_templates
                    SET definition = jsonb_set(jsonb_set(definition, '{defaultShell,search}', '"OFF"'),
                                               '{capabilities}', '["PAGES"]')
                    WHERE id = ?""", MUSEUM);
            mockMvc.perform(authenticated(get("/api/v1/projects/{p}/theme", PROJECT_A)))
                    .andExpect(jsonPath("$.shell.search").value("HEADER"));
            assertThat(jdbc.queryForObject("SELECT feature_configuration->>'EVENTS' FROM project_settings "
                    + "WHERE project_id = ?", String.class, PROJECT_A)).isEqualTo("true");
        } finally {
            jdbc.update("UPDATE application_templates SET definition = ?::jsonb WHERE id = ?", original, MUSEUM);
        }
    }

    @Test
    void initializationIsProjectScoped() throws Exception {
        initializeMuseum(PROJECT_A);
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}", PROJECT_B)))
                .andExpect(jsonPath("$.initialized").value(false))
                .andExpect(jsonPath("$.applicationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.enabledSurfaces").isEmpty());
        mockMvc.perform(authenticated(get("/api/v1/projects/{p}/theme", PROJECT_B)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_INITIALIZED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categories WHERE project_id = ?", Long.class, PROJECT_B))
                .isZero();

        UUID unknown = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
        when(catalogueClient.getProject(unknown)).thenThrow(new CatalogueNotFoundException("gone"));
        initialize(unknown, "{\"templateId\": \"" + MUSEUM + "\"}", 404);
    }

    @Test
    void readinessReportsATemplateViolationOnlyOnceInitialized() throws Exception {
        initializeMuseum(PROJECT_A);
        jdbc.update("UPDATE project_surfaces SET enabled = true WHERE project_id = ? AND surface = 'MOBILE'",
                PROJECT_A);
        jdbc.update("UPDATE project_themes SET text_color = '#FFFFFF' WHERE project_id = ?", PROJECT_A);
        JsonNode readiness = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/readiness", PROJECT_A)))
                .andExpect(status().isOk()).andReturn());
        assertThat(readiness.get("blocking").findValuesAsText("code"))
                .contains("TEMPLATE_VALIDATION_FAILED", "THEME_CONTRAST_FAILED");
        assertThat(readiness.get("blocking").findValuesAsText("path")).contains("surfaces", "theme.textColor");

        JsonNode other = read(mockMvc.perform(authenticated(get("/api/v1/projects/{p}/readiness", PROJECT_B)))
                .andExpect(status().isOk()).andReturn());
        assertThat(other.get("blocking").findValuesAsText("code"))
                .doesNotContain("TEMPLATE_VALIDATION_FAILED", "THEME_CONTRAST_FAILED");
    }

    private long count(String table, UUID projectId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE project_id = ?", Long.class, projectId);
    }

    private static List<String> codes(JsonNode problem) {
        List<String> codes = new java.util.ArrayList<>();
        problem.get("errors").forEach(e -> codes.add(e.get("path").asText() + ":" + e.get("code").asText()));
        return codes;
    }
}
