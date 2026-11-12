package eu.uniche.iguide.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.uniche.iguide.support.PostgresIntegrationTest;
import eu.uniche.iguide.template.domain.TemplateStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Template seeding ({@code 04} sections 5 and 15, {@code 08} Milestone 2; DECISIONS.md D-040): the files are seeded
 * at startup, re-seeding unchanged files changes nothing, and a changed definition of a version that initialized a
 * Project fails the seeding the startup runs.
 */
class TemplateSeedingIntegrationTest extends PostgresIntegrationTest {

    private static final UUID PROJECT = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID OUTDOOR = TemplateSeeder.templateId("outdoor-trail", 1);

    @Autowired
    private TemplateSeeder seeder;

    @Autowired
    private TemplateFileReader reader;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void restore() {
        jdbc.update("DELETE FROM project_themes WHERE project_id = ?", PROJECT);
        jdbc.update("DELETE FROM project_template_applications WHERE project_id = ?", PROJECT);
        jdbc.update("DELETE FROM projects WHERE id = ?", PROJECT);
        seeder.seed(templateFiles(), presetFiles());
    }

    @Test
    void startupSeededEveryFile() {
        assertThat(jdbc.queryForList("SELECT template_key || '.v' || version FROM application_templates "
                + "ORDER BY template_key", String.class))
                .containsExactly("multi-region-network.v1", "museum-multi-surface.v1", "outdoor-trail.v1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM style_presets", Long.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT supported_surfaces::text FROM application_templates WHERE id = ?",
                String.class, OUTDOOR)).isEqualTo("[\"WEB\", \"MOBILE\"]");
    }

    @Test
    void reseedingUnchangedFilesChangesNothing() {
        List<Object> before = jdbc.queryForList("SELECT updated_at FROM application_templates ORDER BY id",
                Object.class);
        seeder.seed(templateFiles(), presetFiles());
        assertThat(jdbc.queryForList("SELECT updated_at FROM application_templates ORDER BY id", Object.class))
                .isEqualTo(before);
    }

    @Test
    void aChangedFileForAnAppliedVersionFailsSeeding() {
        applyOutdoorTrail();
        String checksum = checksum();
        List<TemplateFile> changed = changeOutdoor(d -> {
            ((ObjectNode) d.get("defaultShell")).put("search", "OFF");
            return d;
        });
        assertThatThrownBy(() -> seeder.seed(changed, presetFiles()))
                .isInstanceOf(TemplateSeedingException.class)
                .hasMessageContaining("outdoor-trail.v1.json")
                .hasMessageContaining("already initialized a Project");
        assertThat(checksum()).isEqualTo(checksum);
    }

    @Test
    void anAppliedVersionMayStillChangeStatusNameAndDescription() {
        applyOutdoorTrail();
        List<TemplateFile> files = new ArrayList<>();
        for (TemplateFile file : templateFiles()) {
            files.add(!file.key().equals("outdoor-trail") ? file : new TemplateFile(file.filename(), file.key(),
                    file.version(), TemplateStatus.DEPRECATED, file.name(), file.description(), file.definition(),
                    file.definitionNode()));
        }
        seeder.seed(files, presetFiles());
        assertThat(jdbc.queryForObject("SELECT status FROM application_templates WHERE id = ?", String.class, OUTDOOR))
                .isEqualTo("DEPRECATED");
    }

    @Test
    void aChangedFileForAVersionNoProjectUsesIsReseeded() {
        String checksum = checksum();
        seeder.seed(changeOutdoor(d -> {
            ((ObjectNode) d.get("defaultShell")).put("search", "OFF");
            return d;
        }), presetFiles());
        assertThat(checksum()).isNotEqualTo(checksum);
        assertThat(jdbc.queryForObject("SELECT definition->'defaultShell'->>'search' FROM application_templates "
                + "WHERE id = ?", String.class, OUTDOOR)).isEqualTo("OFF");
    }

    @Test
    void aChangedPresetAProjectUsesFailsSeeding() {
        applyOutdoorTrail();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO project_themes (project_id, style_preset_key, created_at, updated_at) "
                + "VALUES (?, 'quarry-trail', ?::timestamptz, ?::timestamptz)", PROJECT, now.toString(), now.toString());
        List<StylePresetFile> presets = new ArrayList<>();
        for (StylePresetFile file : presetFiles()) {
            StyleValues v = file.values();
            presets.add(!file.key().equals("quarry-trail") ? file : new StylePresetFile(file.filename(), file.key(),
                    file.name(), file.description(), new StyleValues(v.headingFont(), v.bodyFont(), "#000000",
                    v.secondaryColor(), v.backgroundColor(), v.textColor(), v.cornerRadius(), v.density())));
        }
        assertThatThrownBy(() -> seeder.seed(templateFiles(), presets))
                .isInstanceOf(TemplateSeedingException.class)
                .hasMessageContaining("quarry-trail.json");
    }

    @Test
    void anInvalidFileFailsSeeding() {
        List<TemplateFile> unknownPreset = changeOutdoor(d -> {
            d.putArray("stylePresets").add("gallery-house").add("no-such-preset");
            d.put("defaultStylePreset", "gallery-house");
            return d;
        });
        assertThatThrownBy(() -> seeder.seed(unknownPreset, presetFiles()))
                .isInstanceOf(TemplateSeedingException.class)
                .hasMessageContaining("unknown preset no-such-preset");

        List<StylePresetFile> poor = new ArrayList<>();
        for (StylePresetFile file : presetFiles()) {
            StyleValues v = file.values();
            poor.add(!file.key().equals("street-notes") ? file : new StylePresetFile(file.filename(), file.key(),
                    file.name(), file.description(), new StyleValues(v.headingFont(), v.bodyFont(), v.primaryColor(),
                    v.secondaryColor(), v.backgroundColor(), "#AAAAAA", v.cornerRadius(), v.density())));
        }
        assertThatThrownBy(() -> seeder.seed(templateFiles(), poor))
                .isInstanceOf(TemplateSeedingException.class)
                .hasMessageContaining("textColor on backgroundColor");
    }

    private void applyOutdoorTrail() {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO projects (id, organization_id, catalogue_name, last_synchronized_at, created_at, "
                + "updated_at) VALUES (?, ?, 'Seeding', ?::timestamptz, ?::timestamptz, ?::timestamptz)",
                PROJECT, UUID.randomUUID(), now.toString(), now.toString(), now.toString());
        jdbc.update("INSERT INTO project_template_applications (id, project_id, template_id, template_key_snapshot, "
                + "template_version_snapshot, applied_at, applied_by_subject, original_template_snapshot) "
                + "VALUES (?, ?, ?, 'outdoor-trail', 1, ?::timestamptz, 'subject-1', '{}'::jsonb)",
                UUID.randomUUID(), PROJECT, OUTDOOR, now.toString());
    }

    private List<TemplateFile> changeOutdoor(UnaryOperator<ObjectNode> change) {
        List<TemplateFile> files = new ArrayList<>();
        for (TemplateFile file : templateFiles()) {
            if (!file.key().equals("outdoor-trail")) {
                files.add(file);
                continue;
            }
            ObjectNode node = change.apply(file.definitionNode().deepCopy());
            try {
                files.add(new TemplateFile(file.filename(), file.key(), file.version(), file.status(), file.name(),
                        file.description(), objectMapper.treeToValue(node, TemplateDefinition.class), node));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return files;
    }

    private String checksum() {
        return jdbc.queryForObject("SELECT checksum FROM application_templates WHERE id = ?", String.class, OUTDOOR);
    }

    private List<TemplateFile> templateFiles() {
        return reader.templates(TemplateFileReader.TEMPLATE_LOCATION);
    }

    private List<StylePresetFile> presetFiles() {
        return reader.presets(TemplateFileReader.PRESET_LOCATION);
    }
}
