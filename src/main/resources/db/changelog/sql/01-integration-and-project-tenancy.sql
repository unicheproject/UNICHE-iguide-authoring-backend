--liquibase formatted sql

-- Migration group 1: integration and Project tenancy.
--
-- Three tables, none of which the authoring tool owns the truth of. `projects` is a companion row
-- for a Catalogue-owned Project; the other two are non-authoritative snapshots kept for display,
-- reporting, and audit readability. No membership, role, invitation, or access flag is stored
-- anywhere here: the Catalogue authorizes, and local rows never grant access.
--
-- A merged changeset is immutable. Corrections are new changesets, never edits.

--changeset iguide:0001-create-projects
--comment Project companion row, keyed by the canonical Catalogue Project UUID
CREATE TABLE projects (
    id                    uuid        NOT NULL,
    organization_id       uuid        NOT NULL,
    catalogue_name        text        NOT NULL,
    catalogue_slug        text,
    catalogue_status      text,
    catalogue_created_at  timestamptz,
    catalogue_updated_at  timestamptz,
    last_synchronized_at  timestamptz NOT NULL,
    local_description     text,
    initialized_at        timestamptz,
    locally_deleted_at    timestamptz,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    version               bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_projects PRIMARY KEY (id)
);

COMMENT ON TABLE projects IS
    'Companion row for a Catalogue-owned Project. Never proof of access.';
COMMENT ON COLUMN projects.id IS
    'The Catalogue Project UUID used directly as the local primary key. Never generated locally.';
COMMENT ON COLUMN projects.locally_deleted_at IS
    'Set when the Catalogue answered 404 for this Project on a read. Lazy reconciliation only.';
--rollback DROP TABLE projects;

--changeset iguide:0002-index-projects-organization
--comment Organization-scoped project listings skip rows the Catalogue has stopped recognizing
CREATE INDEX idx_projects_organization_id_live
    ON projects (organization_id)
    WHERE locally_deleted_at IS NULL;
--rollback DROP INDEX idx_projects_organization_id_live;

--changeset iguide:0003-create-catalogue-organization-snapshots
--comment Non-authoritative organization snapshot for display, reporting, and audit context
CREATE TABLE catalogue_organization_snapshots (
    id                    uuid        NOT NULL,
    name                  text        NOT NULL,
    slug                  text,
    status                text,
    catalogue_created_at  timestamptz,
    catalogue_updated_at  timestamptz,
    last_synchronized_at  timestamptz NOT NULL,
    CONSTRAINT pk_catalogue_organization_snapshots PRIMARY KEY (id)
);

COMMENT ON TABLE catalogue_organization_snapshots IS
    'Snapshot of a Catalogue organization. Not an Organization: no memberships, no manager '
    'relationships, no authorization flags.';
--rollback DROP TABLE catalogue_organization_snapshots;

--changeset iguide:0004-create-identity-snapshots
--comment Non-authoritative identity snapshot, provisioned just in time from validated tokens
CREATE TABLE identity_snapshots (
    id                  uuid        NOT NULL,
    issuer              text        NOT NULL,
    subject             text        NOT NULL,
    email               text,
    preferred_username  text,
    display_name        text,
    first_seen_at       timestamptz NOT NULL,
    last_seen_at        timestamptz NOT NULL,
    CONSTRAINT pk_identity_snapshots PRIMARY KEY (id),
    CONSTRAINT uq_identity_snapshots_issuer_subject UNIQUE (issuer, subject)
);

COMMENT ON TABLE identity_snapshots IS
    'Snapshot of an authenticated subject. Not a User: no passwords, no access flags, no project or '
    'organization membership, no local account lifecycle controlling authorization.';
COMMENT ON CONSTRAINT uq_identity_snapshots_issuer_subject ON identity_snapshots IS
    'Two concurrent first requests for one new subject both attempt an insert. The loser catches '
    'this violation, re-reads the winning row, and updates it.';
--rollback DROP TABLE identity_snapshots;
