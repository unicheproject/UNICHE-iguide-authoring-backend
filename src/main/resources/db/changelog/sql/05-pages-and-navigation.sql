--liquibase formatted sql

-- Migration group 5: pages and navigation.
--
-- The Navigation menus of the visitor app (`03` section 13, `06` sections 11 and 12, Milestone 6): one PRIMARY
-- tree and one flat FOOTER list per Project, saved live, without lifecycle or working copy. Pages come later in
-- this group; until then an item cannot target a Page (DECISIONS.md D-046).
--
-- A merged changeset is immutable. Corrections are new changesets, never edits.

--changeset iguide:0045-create-navigations
--comment Navigation (03 section 13): at most one per context, PRIMARY shared by every surface and FOOTER links
CREATE TABLE navigations (
    id                 uuid        NOT NULL,
    project_id         uuid        NOT NULL,
    name               text        NOT NULL,
    context            text        NOT NULL,
    configuration      jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    created_by_subject text        NOT NULL,
    updated_by_subject text        NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_navigations PRIMARY KEY (id),
    CONSTRAINT fk_navigations_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_navigations_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_navigations_context UNIQUE (project_id, context),
    CONSTRAINT ck_navigations_context CHECK (context IN ('PRIMARY', 'FOOTER'))
);
--rollback DROP TABLE navigations;

--changeset iguide:0046-create-navigation-items
--comment NavigationItem (03 section 13): a RESOURCE link, a PAGE link once Pages exist, or a GROUP heading of the PRIMARY tree
CREATE TABLE navigation_items (
    id                   uuid    NOT NULL,
    project_id           uuid    NOT NULL,
    navigation_id        uuid    NOT NULL,
    parent_item_id       uuid,
    item_type            text    NOT NULL,
    target_page_id       uuid,
    target_resource_type text,
    target_resource_id   uuid,
    sort_order           integer NOT NULL,
    configuration        jsonb   NOT NULL DEFAULT '{}'::jsonb,
    version              bigint  NOT NULL DEFAULT 0,
    CONSTRAINT pk_navigation_items PRIMARY KEY (id),
    CONSTRAINT uq_navigation_items_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_navigation_items_navigation_id UNIQUE (project_id, navigation_id, id),
    CONSTRAINT fk_navigation_items_navigation FOREIGN KEY (project_id, navigation_id)
        REFERENCES navigations (project_id, id) ON DELETE CASCADE,
    -- The parent is an item of the same Navigation; removing it removes its children.
    CONSTRAINT fk_navigation_items_parent FOREIGN KEY (project_id, navigation_id, parent_item_id)
        REFERENCES navigation_items (project_id, navigation_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_navigation_items_order UNIQUE NULLS NOT DISTINCT (project_id, navigation_id, parent_item_id, sort_order)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_navigation_items_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_navigation_items_type CHECK (item_type IN ('RESOURCE', 'PAGE', 'GROUP')),
    CONSTRAINT ck_navigation_items_resource_type
        CHECK (target_resource_type IS NULL OR target_resource_type IN ('PLACE', 'POI', 'ROUTE', 'GUIDE', 'EVENT')),
    CONSTRAINT ck_navigation_items_target CHECK (
        (item_type = 'RESOURCE' AND target_resource_type IS NOT NULL AND target_resource_id IS NOT NULL
            AND target_page_id IS NULL)
        OR (item_type = 'PAGE' AND target_page_id IS NOT NULL AND target_resource_type IS NULL
            AND target_resource_id IS NULL)
        OR (item_type = 'GROUP' AND target_page_id IS NULL AND target_resource_type IS NULL
            AND target_resource_id IS NULL))
);

CREATE INDEX idx_navigation_items_target ON navigation_items (project_id, target_resource_type, target_resource_id);

COMMENT ON COLUMN navigation_items.target_resource_id IS
    'Polymorphic: same-Project existence and status are checked by the service.';
COMMENT ON COLUMN navigation_items.target_page_id IS
    'Pages do not exist yet (Milestone 6); the service refuses PAGE items until they do.';
--rollback DROP TABLE navigation_items;

--changeset iguide:0047-create-navigation-item-translations
--comment NavigationItemTranslation (03 section 13): the label per language; without a row the target's title is used
CREATE TABLE navigation_item_translations (
    id                 uuid NOT NULL,
    project_id         uuid NOT NULL,
    navigation_item_id uuid NOT NULL,
    language_code      text NOT NULL,
    label              text,
    provenance         jsonb,
    CONSTRAINT pk_navigation_item_translations PRIMARY KEY (id),
    CONSTRAINT fk_navigation_item_translations_item FOREIGN KEY (project_id, navigation_item_id)
        REFERENCES navigation_items (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_navigation_item_translations UNIQUE (project_id, navigation_item_id, language_code)
);

COMMENT ON COLUMN navigation_item_translations.provenance IS
    'Content provenance (03 section 1); null means written by a person.';
--rollback DROP TABLE navigation_item_translations;
