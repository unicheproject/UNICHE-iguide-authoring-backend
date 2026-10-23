--liquibase formatted sql

-- Migration group 2: templates and themes.
--
-- Project configuration (`03` section 2, `04`). So far only the surfaces of a Project, with the WEB surface's
-- public web address; Templates, settings, and themes join with Milestone 2 (DECISIONS.md D-029).
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
