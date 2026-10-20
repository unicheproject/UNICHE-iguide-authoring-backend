--liquibase formatted sql

-- Migration group 3: assets and localization.
--
-- Project Languages (`03` section 3), then Assets, their translations, and ResourceAsset bindings
-- (`03` section 4, Milestone 3). Asset bytes live in S3-compatible storage under `storage_key`.
--
-- A merged changeset is immutable. Corrections are new changesets, never edits.

--changeset iguide:0005-create-project-languages
--comment The languages of a Project; exactly one enabled language is the default
CREATE TABLE project_languages (
    project_id    uuid        NOT NULL,
    language_code text        NOT NULL,
    display_name  text        NOT NULL,
    direction     text        NOT NULL,
    enabled       boolean     NOT NULL,
    is_default    boolean     NOT NULL,
    sort_order    integer     NOT NULL,
    CONSTRAINT pk_project_languages PRIMARY KEY (project_id, language_code),
    CONSTRAINT fk_project_languages_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT ck_project_languages_direction CHECK (direction IN ('LTR', 'RTL')),
    CONSTRAINT ck_project_languages_default_enabled CHECK (NOT is_default OR enabled)
);

CREATE UNIQUE INDEX uq_project_languages_one_default ON project_languages (project_id) WHERE is_default;

COMMENT ON TABLE project_languages IS
    'Languages of a Project. A removed language is disabled, never deleted, so its translations survive.';
COMMENT ON COLUMN project_languages.display_name IS
    'The language''s own name as the visitor sees it in the language switcher, for example Ελληνικά.';
--rollback DROP TABLE project_languages;

--changeset iguide:0028-create-assets
--comment Asset metadata (03 section 4); the bytes live in S3-compatible storage under storage_key
CREATE TABLE assets (
    id                 uuid        NOT NULL,
    project_id         uuid        NOT NULL,
    type               text        NOT NULL,
    status             text        NOT NULL,
    internal_name      text        NOT NULL,
    decorative         boolean     NOT NULL DEFAULT false,
    storage_provider   text        NOT NULL,
    storage_key        text        NOT NULL,
    original_filename  text        NOT NULL,
    mime_type          text        NOT NULL,
    size_bytes         bigint      NOT NULL,
    checksum           text        NOT NULL,
    width              integer,
    height             integer,
    duration_seconds   numeric(10, 3),
    metadata           jsonb       NOT NULL DEFAULT '{}'::jsonb,
    archived_at        timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    created_by_subject text        NOT NULL,
    updated_by_subject text        NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_assets PRIMARY KEY (id),
    CONSTRAINT fk_assets_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_assets_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_assets_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_assets_type CHECK (type IN ('IMAGE', 'AUDIO', 'VIDEO', 'DOCUMENT', 'THREE_D_MODEL', 'FLOOR_PLAN', 'OTHER')),
    CONSTRAINT ck_assets_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_assets_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_assets_dimensions CHECK ((width IS NULL OR width > 0) AND (height IS NULL OR height > 0))
);

CREATE INDEX idx_assets_project_status ON assets (project_id, status);
CREATE INDEX idx_assets_project_created ON assets (project_id, created_at DESC);

COMMENT ON COLUMN assets.storage_key IS
    'Object key in the bucket, projects/{projectId}/assets/{assetId}/original or .../revisions/{uuid} after a replacement. Never a public URL.';
COMMENT ON COLUMN assets.checksum IS 'SHA-256 of the stored bytes, lowercase hex.';
--rollback DROP TABLE assets;

--changeset iguide:0029-create-asset-translations
--comment Title, caption, alternative text, credits, and transcript of an Asset, one row per language
CREATE TABLE asset_translations (
    id               uuid NOT NULL,
    project_id       uuid NOT NULL,
    asset_id         uuid NOT NULL,
    language_code    text NOT NULL,
    title            text,
    caption          text,
    alternative_text text,
    credits          text,
    transcript       text,
    provenance       jsonb,
    CONSTRAINT pk_asset_translations PRIMARY KEY (id),
    CONSTRAINT fk_asset_translations_asset_same_project FOREIGN KEY (project_id, asset_id)
        REFERENCES assets (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_asset_translations_language UNIQUE (project_id, asset_id, language_code)
);
--rollback DROP TABLE asset_translations;

--changeset iguide:0030-create-resource-assets
--comment An Asset bound to a Resource in a role; the Resource side is a generic reference checked by the service
CREATE TABLE resource_assets (
    id            uuid    NOT NULL,
    project_id    uuid    NOT NULL,
    resource_type text    NOT NULL,
    resource_id   uuid    NOT NULL,
    asset_id      uuid    NOT NULL,
    usage         text    NOT NULL,
    language_code text,
    sort_order    integer NOT NULL,
    configuration jsonb   NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_resource_assets PRIMARY KEY (id),
    CONSTRAINT fk_resource_assets_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_resource_assets_asset_same_project FOREIGN KEY (project_id, asset_id)
        REFERENCES assets (project_id, id),
    CONSTRAINT ck_resource_assets_resource_type CHECK (resource_type IN ('POI', 'ROUTE', 'GUIDE', 'EVENT')),
    CONSTRAINT ck_resource_assets_usage CHECK (usage IN ('HERO_IMAGE', 'THUMBNAIL', 'GALLERY_IMAGE', 'VIDEO',
        'AUDIO_NARRATION', 'ATTACHMENT', 'MODEL', 'MODEL_POSTER', 'INLINE'))
);

CREATE INDEX idx_resource_assets_resource ON resource_assets (project_id, resource_type, resource_id, sort_order);
CREATE INDEX idx_resource_assets_asset ON resource_assets (project_id, asset_id);
-- One INLINE row per Asset and Resource: the reconciliation inserts missing ids and deletes stale ones.
CREATE UNIQUE INDEX uq_resource_assets_inline ON resource_assets (project_id, resource_type, resource_id, asset_id)
    WHERE usage = 'INLINE';

COMMENT ON COLUMN resource_assets.language_code IS
    'Null applies to every language; otherwise the binding is for visitors of that language (03 section 4).';
--rollback DROP TABLE resource_assets;
