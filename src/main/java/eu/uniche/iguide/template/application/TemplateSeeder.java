package eu.uniche.iguide.template.application;

import eu.uniche.iguide.template.domain.ApplicationTemplate;
import eu.uniche.iguide.template.domain.StylePreset;
import eu.uniche.iguide.template.infrastructure.ApplicationTemplateRepository;
import eu.uniche.iguide.template.infrastructure.ProjectTemplateApplicationRepository;
import eu.uniche.iguide.template.infrastructure.StylePresetRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds the Template and style preset files into the database at startup ({@code 04} section 15), before the web
 * server accepts requests:
 *
 * <ul>
 *   <li>an unknown {@code (key, version)} or preset key is inserted;
 *   <li>a known one whose checksum still matches only takes the file's status, name, and description;
 *   <li>a changed checksum is written when no Project uses the version (or the preset); otherwise startup fails,
 *       which is how a version that initialized a Project stays immutable ({@code 04} section 5).
 * </ul>
 *
 * <p>A Template's id is derived from its key and version, so every environment names a version alike.
 */
@Component
public class TemplateSeeder implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TemplateSeeder.class);

    private final TemplateFileReader reader;
    private final ApplicationTemplateRepository templates;
    private final StylePresetRepository presets;
    private final ProjectTemplateApplicationRepository applications;
    private final TransactionTemplate transactionTemplate;

    public TemplateSeeder(TemplateFileReader reader, ApplicationTemplateRepository templates,
                          StylePresetRepository presets, ProjectTemplateApplicationRepository applications,
                          TransactionTemplate transactionTemplate) {
        this.reader = reader;
        this.templates = templates;
        this.presets = presets;
        this.applications = applications;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<TemplateFile> templateFiles = reader.templates(TemplateFileReader.TEMPLATE_LOCATION);
        List<StylePresetFile> presetFiles = reader.presets(TemplateFileReader.PRESET_LOCATION);
        seed(templateFiles, presetFiles);
        log.info("Seeded {} Template versions and {} style presets", templateFiles.size(), presetFiles.size());
    }

    public static UUID templateId(String key, int version) {
        return UUID.nameUUIDFromBytes(("iguide:template:" + key + ":v" + version).getBytes(StandardCharsets.UTF_8));
    }

    /** Validates and seeds the given files in one transaction. */
    public void seed(List<TemplateFile> templateFiles, List<StylePresetFile> presetFiles) {
        TemplateFileValidator.validate(templateFiles, presetFiles);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transactionTemplate.executeWithoutResult(status -> {
            presetFiles.forEach(file -> seedPreset(file, now));
            templateFiles.forEach(file -> seedTemplate(file, now));
        });
    }

    private void seedPreset(StylePresetFile file, Instant now) {
        String checksum = reader.checksum(file.values().asMap());
        StylePreset row = presets.findById(file.key()).orElse(null);
        if (row == null) {
            row = StylePreset.seed(file.key(), now);
            row.write(file.name(), file.description(), file.values().asMap(), checksum, now);
        } else if (!row.getChecksum().equals(checksum)) {
            if (presets.isUsedByAnyProject(file.key())) {
                throw new TemplateSeedingException("Style preset file " + file.filename() + " changed the values of"
                        + " a preset a Project uses; add a preset under a new key instead.", null);
            }
            row.write(file.name(), file.description(), file.values().asMap(), checksum, now);
        } else if (!row.getName().equals(file.name()) || !row.getDescription().equals(file.description())) {
            row.describe(file.name(), file.description(), now);
        } else {
            return;
        }
        presets.save(row);
    }

    private void seedTemplate(TemplateFile file, Instant now) {
        String checksum = reader.checksum(file.definitionNode());
        ApplicationTemplate row = templates.findByKeyAndVersion(file.key(), file.version()).orElse(null);
        List<String> surfaces = file.definition().supportedSurfaces().stream().map(Enum::name).toList();
        if (row == null) {
            row = ApplicationTemplate.seed(templateId(file.key(), file.version()), file.key(), file.version(), now);
            row.write(file.status(), file.name(), file.description(), surfaces, reader.asMap(file.definitionNode()),
                    checksum, now);
        } else if (!row.getChecksum().equals(checksum)) {
            if (applications.existsByTemplateId(row.getId())) {
                throw new TemplateSeedingException("Template file " + file.filename() + " changed the definition of"
                        + " version " + file.version() + ", which already initialized a Project; release it as a new"
                        + " version instead (04 section 5).", null);
            }
            row.write(file.status(), file.name(), file.description(), surfaces, reader.asMap(file.definitionNode()),
                    checksum, now);
        } else if (row.getStatus() != file.status() || !Objects.equals(row.getName(), file.name())
                || !Objects.equals(row.getDescription(), file.description())) {
            row.describe(file.status(), file.name(), file.description(), now);
        } else {
            return;
        }
        templates.save(row);
    }
}
