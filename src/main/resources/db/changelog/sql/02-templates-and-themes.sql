--liquibase formatted sql

-- Migration group 2: templates and themes.
--
-- Project configuration (`03` section 2, `04`): the surfaces of a Project with the WEB surface's public web address
-- (DECISIONS.md D-029), then Milestone 2 (D-040): Templates and style presets (platform-owned, seeded at startup),
-- Template provenance, Project settings, and Project themes. No Row-Level Security yet (GAPS.md G-009, G-022).
--
-- A merged changeset is immutable. Corrections are new changesets, never edits.

--changeset iguide:0035-create-project-surfaces
--comment ProjectSurface (03 section 2): which visitor surfaces a Project offers, and their configuration
CREATE TABLE project_surfaces (
    project_id    uuid        NOT NULL,
    surface       text        NOT NULL,
    enabled       boolean     NOT NULL,
    sort_order    integer     NOT NULL,
    configuration jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    version       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_project_surfaces PRIMARY KEY (project_id, surface),
    CONSTRAINT fk_project_surfaces_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT ck_project_surfaces_surface CHECK (surface IN ('WEB', 'MOBILE', 'KIOSK'))
);

COMMENT ON COLUMN project_surfaces.configuration IS
    'Per-surface runtime options (04 section 6); WEB: publicBaseUrl, the origin where the web build is hosted.';
--rollback DROP TABLE project_surfaces;

--changeset iguide:0037-create-application-templates
--comment ApplicationTemplate (04 sections 3 to 6): platform-owned, immutable versions seeded from repository files
CREATE TABLE application_templates (
    id                 uuid        NOT NULL,
    template_key       text        NOT NULL,
    version            integer     NOT NULL,
    status             text        NOT NULL,
    name               jsonb       NOT NULL,
    description        jsonb       NOT NULL,
    supported_surfaces jsonb       NOT NULL,
    definition         jsonb       NOT NULL,
    checksum           text        NOT NULL,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    created_by_subject text        NOT NULL,
    CONSTRAINT pk_application_templates PRIMARY KEY (id),
    CONSTRAINT uq_application_templates_key_version UNIQUE (template_key, version),
    CONSTRAINT ck_application_templates_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')),
    CONSTRAINT ck_application_templates_version CHECK (version > 0)
);

COMMENT ON TABLE application_templates IS
    'Platform-owned Template versions, not Project data. Written only by the startup seeder from src/main/resources/templates.';
COMMENT ON COLUMN application_templates.checksum IS
    'SHA-256 of the canonical JSON of definition. A changed checksum on a version that initialized a Project fails startup.';
--rollback DROP TABLE application_templates;

--changeset iguide:0038-create-style-presets
--comment Style presets (04 section 9): platform-owned, seeded from src/main/resources/style-presets
CREATE TABLE style_presets (
    preset_key    text        NOT NULL,
    name          jsonb       NOT NULL,
    description   jsonb       NOT NULL,
    preset_values jsonb       NOT NULL,
    checksum      text        NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT pk_style_presets PRIMARY KEY (preset_key)
);

COMMENT ON COLUMN style_presets.checksum IS
    'SHA-256 of the canonical JSON of preset_values. A changed checksum on a preset a Project theme uses fails startup.';
--rollback DROP TABLE style_presets;

--changeset iguide:0039-create-project-template-applications
--comment ProjectTemplateApplication (04 section 7): which Template version initialized the Project, one per Project
CREATE TABLE project_template_applications (
    id                         uuid        NOT NULL,
    project_id                 uuid        NOT NULL,
    template_id                uuid        NOT NULL,
    template_key_snapshot      text        NOT NULL,
    template_version_snapshot  integer     NOT NULL,
    applied_at                 timestamptz NOT NULL,
    applied_by_subject         text        NOT NULL,
    original_template_snapshot jsonb       NOT NULL,
    CONSTRAINT pk_project_template_applications PRIMARY KEY (id),
    CONSTRAINT uq_project_template_applications_project UNIQUE (project_id),
    CONSTRAINT uq_project_template_applications_project_id_id UNIQUE (project_id, id),
    CONSTRAINT fk_project_template_applications_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_template_applications_template FOREIGN KEY (template_id)
        REFERENCES application_templates (id)
);

CREATE INDEX idx_project_template_applications_template ON project_template_applications (template_id);
--rollback DROP TABLE project_template_applications;

--changeset iguide:0040-create-project-settings
--comment ProjectSettings (03 section 2), created by initialization; the default language stays in project_languages
CREATE TABLE project_settings (
    project_id            uuid        NOT NULL,
    timezone              text,
    application_status    text        NOT NULL DEFAULT 'DRAFT',
    content_revision      bigint      NOT NULL DEFAULT 0,
    feature_configuration jsonb       NOT NULL DEFAULT '{}'::jsonb,
    runtime_configuration jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    version               bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_project_settings PRIMARY KEY (project_id),
    CONSTRAINT fk_project_settings_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT ck_project_settings_application_status CHECK (application_status IN ('DRAFT', 'LIVE', 'SUSPENDED')),
    CONSTRAINT ck_project_settings_content_revision CHECK (content_revision >= 0)
);

COMMENT ON COLUMN project_settings.feature_configuration IS
    'Capability to enabled, restricted to the capabilities of the applied Template (04 section 6).';
--rollback DROP TABLE project_settings;

--changeset iguide:0041-create-project-themes
--comment ProjectTheme (04 section 9): typed values over the Template defaults, the shell as JSONB
CREATE TABLE project_themes (
    project_id            uuid        NOT NULL,
    logo_asset_id         uuid,
    compact_logo_asset_id uuid,
    favicon_asset_id      uuid,
    splash_asset_id       uuid,
    primary_color         text,
    secondary_color       text,
    background_color      text,
    text_color            text,
    style_preset_key      text,
    heading_font          text,
    body_font             text,
    corner_radius         text,
    density               text,
    shell                 jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    version               bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_project_themes PRIMARY KEY (project_id),
    CONSTRAINT fk_project_themes_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_themes_style_preset FOREIGN KEY (style_preset_key) REFERENCES style_presets (preset_key),
    CONSTRAINT ck_project_themes_colors CHECK (
        (primary_color IS NULL OR primary_color ~ '^#[0-9A-F]{6}$')
        AND (secondary_color IS NULL OR secondary_color ~ '^#[0-9A-F]{6}$')
        AND (background_color IS NULL OR background_color ~ '^#[0-9A-F]{6}$')
        AND (text_color IS NULL OR text_color ~ '^#[0-9A-F]{6}$')),
    CONSTRAINT ck_project_themes_corner_radius CHECK (corner_radius IS NULL OR corner_radius IN ('SHARP', 'SOFT', 'ROUND')),
    CONSTRAINT ck_project_themes_density CHECK (density IS NULL OR density IN ('COMPACT', 'REGULAR', 'SPACIOUS'))
);

COMMENT ON TABLE project_themes IS
    'A null typed value takes the applied Template''s defaultTheme value (04 section 10). Logos reference Assets of '
    'the same Project through composite foreign keys added with the assets group (changeset 0042).';
--rollback DROP TABLE project_themes;
