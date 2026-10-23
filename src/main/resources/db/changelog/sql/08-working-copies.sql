--liquibase formatted sql

-- Migration group 8: working copies.
--
-- One pending edit per ACTIVE Resource (`03` section 1, `06` section 17, Milestone 8). The target is
-- generic, so `(resource_type, resource_id)` has no foreign key; the application checks it.
--
-- A merged changeset is immutable. Corrections are new changesets, never edits.

--changeset iguide:0033-create-resource-working-copies
--comment ResourceWorkingCopy (03 section 1): the pending edit of one ACTIVE Resource, applied or discarded by the author
CREATE TABLE resource_working_copies (
    id                 uuid        NOT NULL,
    project_id         uuid        NOT NULL,
    resource_type      text        NOT NULL,
    resource_id        uuid        NOT NULL,
    payload            jsonb       NOT NULL,
    base_version       bigint      NOT NULL,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    created_by_subject text        NOT NULL,
    updated_by_subject text        NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_resource_working_copies PRIMARY KEY (id),
    CONSTRAINT fk_resource_working_copies_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_resource_working_copies_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_resource_working_copies_target UNIQUE (project_id, resource_type, resource_id),
    CONSTRAINT ck_resource_working_copies_type CHECK (resource_type IN ('PLACE', 'POI', 'ROUTE', 'GUIDE', 'EVENT',
        'CATEGORY', 'GEOGRAPHIC_MAP', 'FLOOR_PLAN'))
);

COMMENT ON COLUMN resource_working_copies.payload IS
    'The full editable state of the Resource, children included, in the shape of its working-copy body; never queried by field.';
COMMENT ON COLUMN resource_working_copies.base_version IS
    'The version of the live row the copy was made from; apply refuses when the live row moved past it.';
--rollback DROP TABLE resource_working_copies;
