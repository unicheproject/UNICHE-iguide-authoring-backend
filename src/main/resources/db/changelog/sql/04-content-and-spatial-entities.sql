--liquibase formatted sql

-- Migration group 4: content and spatial entities.
--
-- Places (`03` section 5), Geographic Maps (section 7), and Floor Plans (section 8). Every table carries
-- project_id, and every reference between them is a composite (project_id, id) foreign key, so a row
-- can never point into another Project whatever the service layer does (`06` sections 2 and 3).
-- Row-Level Security policies are added with Milestone 1b (GAPS.md G-022).
--
-- A merged changeset is immutable. Corrections are new changesets, never edits.

--changeset iguide:0006-create-places
--comment Place: a region, site, building, floor, room, or area; nested through parent_place_id
CREATE TABLE places (
    id                 uuid          NOT NULL,
    project_id         uuid          NOT NULL,
    parent_place_id    uuid,
    slug               text          NOT NULL,
    type               text          NOT NULL,
    environment        text          NOT NULL,
    status             text          NOT NULL,
    internal_name      text,
    latitude           numeric(9, 6),
    longitude          numeric(9, 6),
    featured           boolean       NOT NULL DEFAULT false,
    sort_order         integer       NOT NULL,
    configuration      jsonb         NOT NULL DEFAULT '{}'::jsonb,
    archived_at        timestamptz,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    created_by_subject text          NOT NULL,
    updated_by_subject text          NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_places PRIMARY KEY (id),
    CONSTRAINT fk_places_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_places_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_places_project_slug UNIQUE (project_id, slug),
    CONSTRAINT fk_places_parent_same_project FOREIGN KEY (project_id, parent_place_id)
        REFERENCES places (project_id, id),
    CONSTRAINT ck_places_not_own_parent CHECK (parent_place_id IS NULL OR parent_place_id <> id),
    CONSTRAINT ck_places_type CHECK (type IN ('REGION', 'DESTINATION', 'SITE', 'BUILDING', 'FLOOR', 'ROOM',
                                              'AREA', 'OTHER')),
    CONSTRAINT ck_places_environment CHECK (environment IN ('INDOOR', 'OUTDOOR')),
    CONSTRAINT ck_places_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_places_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_places_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_places_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_places_project_status_sort ON places (project_id, status, sort_order);
CREATE INDEX idx_places_project_parent ON places (project_id, parent_place_id);

COMMENT ON COLUMN places.environment IS
    'INDOOR links to Floor Plans, OUTDOOR to one Geographic Map; the link lives on the map or plan.';
--rollback DROP TABLE places;

--changeset iguide:0007-create-place-translations
--comment Visitor-facing text of a Place, one row per language
CREATE TABLE place_translations (
    id                        uuid NOT NULL,
    project_id                uuid NOT NULL,
    place_id                  uuid NOT NULL,
    language_code             text NOT NULL,
    title                     text,
    summary                   text,
    description               text,
    visitor_information       text,
    accessibility_information text,
    provenance                jsonb,
    CONSTRAINT pk_place_translations PRIMARY KEY (id),
    CONSTRAINT fk_place_translations_place_same_project FOREIGN KEY (project_id, place_id)
        REFERENCES places (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_place_translations_language UNIQUE (project_id, place_id, language_code)
);

COMMENT ON COLUMN place_translations.provenance IS
    'Content provenance (03 section 1); null means written by a person. Never reaches the manifest.';
--rollback DROP TABLE place_translations;

--changeset iguide:0008-create-geographic-maps
--comment Geographic Map: an outdoor map with an initial view, optional bounds, and what visitors see
CREATE TABLE geographic_maps (
    id                     uuid          NOT NULL,
    project_id             uuid          NOT NULL,
    place_id               uuid,
    status                 text          NOT NULL,
    internal_name          text          NOT NULL,
    initial_latitude       numeric(9, 6) NOT NULL,
    initial_longitude      numeric(9, 6) NOT NULL,
    initial_zoom           numeric(5, 2) NOT NULL,
    min_latitude           numeric(9, 6),
    min_longitude          numeric(9, 6),
    max_latitude           numeric(9, 6),
    max_longitude          numeric(9, 6),
    visibility             jsonb         NOT NULL,
    visitor_filter_enabled boolean       NOT NULL DEFAULT true,
    configuration          jsonb         NOT NULL DEFAULT '{}'::jsonb,
    archived_at            timestamptz,
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    created_by_subject     text          NOT NULL,
    updated_by_subject     text          NOT NULL,
    version                bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_geographic_maps PRIMARY KEY (id),
    CONSTRAINT fk_geographic_maps_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_geographic_maps_project_id_id UNIQUE (project_id, id),
    CONSTRAINT fk_geographic_maps_place_same_project FOREIGN KEY (project_id, place_id)
        REFERENCES places (project_id, id),
    CONSTRAINT ck_geographic_maps_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_geographic_maps_view CHECK (initial_latitude BETWEEN -90 AND 90
        AND initial_longitude BETWEEN -180 AND 180 AND initial_zoom BETWEEN 0 AND 22),
    CONSTRAINT ck_geographic_maps_bounds CHECK (
        (min_latitude IS NULL AND min_longitude IS NULL AND max_latitude IS NULL AND max_longitude IS NULL)
        OR (min_latitude < max_latitude AND min_longitude < max_longitude))
);

CREATE UNIQUE INDEX uq_geographic_maps_place ON geographic_maps (project_id, place_id) WHERE place_id IS NOT NULL;
CREATE INDEX idx_geographic_maps_project_status ON geographic_maps (project_id, status);

COMMENT ON COLUMN geographic_maps.visibility IS
    'Per resource type and Category, never per Resource: {"POI": {"visible", "hiddenCategoryIds"}, "ROUTE": {...}}.';
COMMENT ON COLUMN geographic_maps.configuration IS
    'Author-side details that do not reach visitors, such as the searched area name.';
--rollback DROP TABLE geographic_maps;

--changeset iguide:0009-create-geographic-map-resources
--comment A Place or Point of Interest placed on a Geographic Map, at its coordinates on that map
CREATE TABLE geographic_map_resources (
    id            uuid          NOT NULL,
    project_id    uuid          NOT NULL,
    map_id        uuid          NOT NULL,
    resource_type text          NOT NULL,
    resource_id   uuid          NOT NULL,
    latitude      numeric(9, 6) NOT NULL,
    longitude     numeric(9, 6) NOT NULL,
    presentation  jsonb         NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_geographic_map_resources PRIMARY KEY (id),
    CONSTRAINT fk_geographic_map_resources_map_same_project FOREIGN KEY (project_id, map_id)
        REFERENCES geographic_maps (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_geographic_map_resources_target UNIQUE (project_id, map_id, resource_type, resource_id),
    CONSTRAINT ck_geographic_map_resources_type CHECK (resource_type IN ('POI', 'PLACE')),
    CONSTRAINT ck_geographic_map_resources_position CHECK (latitude BETWEEN -90 AND 90
        AND longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_geographic_map_resources_target
    ON geographic_map_resources (project_id, resource_type, resource_id);

COMMENT ON COLUMN geographic_map_resources.resource_id IS
    'Polymorphic: same-Project existence and status are checked by the service (06 section 5).';
--rollback DROP TABLE geographic_map_resources;

--changeset iguide:0010-create-floor-plans
--comment Floor Plan: an indoor image with Resources at normalized coordinates; a Place may have several
CREATE TABLE floor_plans (
    id                 uuid        NOT NULL,
    project_id         uuid        NOT NULL,
    place_id           uuid,
    asset_id           uuid,
    status             text        NOT NULL,
    internal_name      text        NOT NULL,
    width              integer,
    height             integer,
    coordinate_system  text        NOT NULL DEFAULT 'NORMALIZED',
    sort_order         integer     NOT NULL,
    configuration      jsonb       NOT NULL DEFAULT '{}'::jsonb,
    archived_at        timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    created_by_subject text        NOT NULL,
    updated_by_subject text        NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_floor_plans PRIMARY KEY (id),
    CONSTRAINT fk_floor_plans_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_floor_plans_project_id_id UNIQUE (project_id, id),
    CONSTRAINT fk_floor_plans_place_same_project FOREIGN KEY (project_id, place_id)
        REFERENCES places (project_id, id),
    CONSTRAINT ck_floor_plans_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_floor_plans_coordinate_system CHECK (coordinate_system IN ('NORMALIZED')),
    CONSTRAINT ck_floor_plans_size CHECK ((width IS NULL OR width > 0) AND (height IS NULL OR height > 0))
);

CREATE INDEX idx_floor_plans_project_place_sort ON floor_plans (project_id, place_id, sort_order);
CREATE INDEX idx_floor_plans_project_status ON floor_plans (project_id, status);

COMMENT ON COLUMN floor_plans.asset_id IS
    'The plan image. Nullable and without a foreign key until Assets exist (GAPS.md G-025); required to activate.';
COMMENT ON COLUMN floor_plans.sort_order IS
    'Orders the Floor Plans of one Place, lowest floor first, for the visitor floor switcher.';
--rollback DROP TABLE floor_plans;

--changeset iguide:0011-create-floor-plan-positions
--comment A Place or Point of Interest on a Floor Plan, at normalized x and y from 0 to 1
CREATE TABLE floor_plan_positions (
    id            uuid          NOT NULL,
    project_id    uuid          NOT NULL,
    floor_plan_id uuid          NOT NULL,
    resource_type text          NOT NULL,
    resource_id   uuid          NOT NULL,
    x             numeric(6, 5) NOT NULL,
    y             numeric(6, 5) NOT NULL,
    rotation      integer,
    presentation  jsonb         NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_floor_plan_positions PRIMARY KEY (id),
    CONSTRAINT fk_floor_plan_positions_plan_same_project FOREIGN KEY (project_id, floor_plan_id)
        REFERENCES floor_plans (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_floor_plan_positions_target UNIQUE (project_id, floor_plan_id, resource_type, resource_id),
    CONSTRAINT ck_floor_plan_positions_type CHECK (resource_type IN ('POI', 'PLACE')),
    CONSTRAINT ck_floor_plan_positions_xy CHECK (x BETWEEN 0 AND 1 AND y BETWEEN 0 AND 1),
    CONSTRAINT ck_floor_plan_positions_rotation CHECK (rotation IS NULL OR rotation BETWEEN 0 AND 359)
);

CREATE INDEX idx_floor_plan_positions_target ON floor_plan_positions (project_id, resource_type, resource_id);
--rollback DROP TABLE floor_plan_positions;

--changeset iguide:0012-create-points-of-interest
--comment Point of Interest: a reusable visitor-facing object or spot, optionally inside a Place [ARC_3.3.4]
CREATE TABLE points_of_interest (
    id                      uuid          NOT NULL,
    project_id              uuid          NOT NULL,
    place_id                uuid,
    slug                    text          NOT NULL,
    code                    text,
    status                  text          NOT NULL,
    internal_name           text,
    latitude                numeric(9, 6),
    longitude               numeric(9, 6),
    estimated_visit_minutes integer,
    featured                boolean       NOT NULL DEFAULT false,
    sort_order              integer       NOT NULL,
    configuration           jsonb         NOT NULL DEFAULT '{}'::jsonb,
    archived_at             timestamptz,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by_subject      text          NOT NULL,
    updated_by_subject      text          NOT NULL,
    version                 bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_points_of_interest PRIMARY KEY (id),
    CONSTRAINT fk_points_of_interest_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_points_of_interest_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_points_of_interest_project_slug UNIQUE (project_id, slug),
    CONSTRAINT fk_points_of_interest_place_same_project FOREIGN KEY (project_id, place_id)
        REFERENCES places (project_id, id),
    CONSTRAINT ck_points_of_interest_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_points_of_interest_code CHECK (code IS NULL
        OR (length(code) <= 32 AND code ~ '^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$')),
    CONSTRAINT ck_points_of_interest_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_points_of_interest_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_points_of_interest_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_points_of_interest_visit_minutes CHECK (estimated_visit_minutes IS NULL
        OR estimated_visit_minutes BETWEEN 1 AND 1440)
);

CREATE UNIQUE INDEX uq_points_of_interest_project_code ON points_of_interest (project_id, code) WHERE code IS NOT NULL;
CREATE INDEX idx_points_of_interest_project_status_sort ON points_of_interest (project_id, status, sort_order);
CREATE INDEX idx_points_of_interest_project_place ON points_of_interest (project_id, place_id);

COMMENT ON COLUMN points_of_interest.code IS
    'The exhibit number a visitor types to open the POI; unique per Project where set. Not a trigger code.';
--rollback DROP TABLE points_of_interest;

--changeset iguide:0013-create-poi-translations
--comment Visitor-facing text of a Point of Interest, one row per language
CREATE TABLE poi_translations (
    id                        uuid NOT NULL,
    project_id                uuid NOT NULL,
    poi_id                    uuid NOT NULL,
    language_code             text NOT NULL,
    title                     text,
    summary                   text,
    description               text,
    instructions              text,
    accessibility_information text,
    provenance                jsonb,
    CONSTRAINT pk_poi_translations PRIMARY KEY (id),
    CONSTRAINT fk_poi_translations_poi_same_project FOREIGN KEY (project_id, poi_id)
        REFERENCES points_of_interest (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_poi_translations_language UNIQUE (project_id, poi_id, language_code)
);
--rollback DROP TABLE poi_translations;

--changeset iguide:0014-create-routes
--comment Route: an ordered journey through Points of Interest, indoors or outdoors [ARC_3.3.4]
CREATE TABLE routes (
    id                         uuid        NOT NULL,
    project_id                 uuid        NOT NULL,
    slug                       text        NOT NULL,
    status                     text        NOT NULL,
    type                       text        NOT NULL,
    travel_mode                text,
    estimated_duration_minutes integer,
    distance_meters            integer,
    difficulty                 text,
    loop                       boolean     NOT NULL DEFAULT false,
    internal_name              text,
    featured                   boolean     NOT NULL DEFAULT false,
    sort_order                 integer     NOT NULL,
    configuration              jsonb       NOT NULL DEFAULT '{}'::jsonb,
    archived_at                timestamptz,
    created_at                 timestamptz NOT NULL,
    updated_at                 timestamptz NOT NULL,
    created_by_subject         text        NOT NULL,
    updated_by_subject         text        NOT NULL,
    version                    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_routes PRIMARY KEY (id),
    CONSTRAINT fk_routes_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_routes_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_routes_project_slug UNIQUE (project_id, slug),
    CONSTRAINT ck_routes_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_routes_type CHECK (type IN ('INDOOR', 'OUTDOOR')),
    CONSTRAINT ck_routes_travel_mode CHECK (travel_mode IS NULL
        OR travel_mode IN ('WALKING', 'HIKING', 'BIKING', 'CAR', 'SAILING')),
    CONSTRAINT ck_routes_difficulty CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MODERATE', 'HARD')),
    CONSTRAINT ck_routes_duration CHECK (estimated_duration_minutes IS NULL
        OR estimated_duration_minutes BETWEEN 1 AND 10080),
    CONSTRAINT ck_routes_distance CHECK (distance_meters IS NULL OR distance_meters BETWEEN 1 AND 1000000)
);

CREATE INDEX idx_routes_project_status_sort ON routes (project_id, status, sort_order);

COMMENT ON COLUMN routes.distance_meters IS
    'The author''s override; when null, the path length through the stops'' coordinates is used (03 section 9).';
--rollback DROP TABLE routes;

--changeset iguide:0015-create-route-translations
--comment Visitor-facing text of a Route, one row per language
CREATE TABLE route_translations (
    id            uuid NOT NULL,
    project_id    uuid NOT NULL,
    route_id      uuid NOT NULL,
    language_code text NOT NULL,
    title         text,
    summary       text,
    description   text,
    instructions  text,
    provenance    jsonb,
    CONSTRAINT pk_route_translations PRIMARY KEY (id),
    CONSTRAINT fk_route_translations_route_same_project FOREIGN KEY (project_id, route_id)
        REFERENCES routes (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_route_translations_language UNIQUE (project_id, route_id, language_code)
);
--rollback DROP TABLE route_translations;

--changeset iguide:0016-create-route-stops
--comment A Route Stop: a position in the Route bound to a Point of Interest of the same Project
CREATE TABLE route_stops (
    id            uuid    NOT NULL,
    project_id    uuid    NOT NULL,
    route_id      uuid    NOT NULL,
    poi_id        uuid    NOT NULL,
    position      integer NOT NULL,
    configuration jsonb   NOT NULL DEFAULT '{}'::jsonb,
    version       bigint  NOT NULL DEFAULT 0,
    CONSTRAINT pk_route_stops PRIMARY KEY (id),
    CONSTRAINT uq_route_stops_project_id_id UNIQUE (project_id, id),
    CONSTRAINT fk_route_stops_route_same_project FOREIGN KEY (project_id, route_id)
        REFERENCES routes (project_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_route_stop_poi_same_project FOREIGN KEY (project_id, poi_id)
        REFERENCES points_of_interest (project_id, id),
    CONSTRAINT uq_route_stops_poi UNIQUE (project_id, route_id, poi_id),
    CONSTRAINT uq_route_stops_position UNIQUE (project_id, route_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_route_stops_position CHECK (position >= 0)
);

CREATE INDEX idx_route_stops_project_poi ON route_stops (project_id, poi_id);
--rollback DROP TABLE route_stops;

--changeset iguide:0017-create-route-stop-translations
--comment Route-specific text of a stop; it never changes the Point of Interest's own text
CREATE TABLE route_stop_translations (
    id                      uuid NOT NULL,
    project_id              uuid NOT NULL,
    route_stop_id           uuid NOT NULL,
    language_code           text NOT NULL,
    title_override          text,
    description             text,
    navigation_instructions text,
    narrative               text,
    provenance              jsonb,
    CONSTRAINT pk_route_stop_translations PRIMARY KEY (id),
    CONSTRAINT fk_route_stop_translations_stop_same_project FOREIGN KEY (project_id, route_stop_id)
        REFERENCES route_stops (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_route_stop_translations_language UNIQUE (project_id, route_stop_id, language_code)
);
--rollback DROP TABLE route_stop_translations;

--changeset iguide:0018-create-guides
--comment Guide: an article of ordered text and reference blocks (03 section 11)
CREATE TABLE guides (
    id                        uuid        NOT NULL,
    project_id                uuid        NOT NULL,
    slug                      text        NOT NULL,
    status                    text        NOT NULL,
    internal_name             text,
    estimated_reading_minutes integer,
    featured                  boolean     NOT NULL DEFAULT false,
    sort_order                integer     NOT NULL,
    configuration             jsonb       NOT NULL DEFAULT '{}'::jsonb,
    archived_at               timestamptz,
    created_at                timestamptz NOT NULL,
    updated_at                timestamptz NOT NULL,
    created_by_subject        text        NOT NULL,
    updated_by_subject        text        NOT NULL,
    version                   bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_guides PRIMARY KEY (id),
    CONSTRAINT fk_guides_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_guides_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_guides_project_slug UNIQUE (project_id, slug),
    CONSTRAINT ck_guides_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_guides_reading CHECK (estimated_reading_minutes IS NULL
        OR estimated_reading_minutes BETWEEN 1 AND 600)
);

CREATE INDEX idx_guides_project_status_sort ON guides (project_id, status, sort_order);
--rollback DROP TABLE guides;

--changeset iguide:0019-create-guide-translations
--comment Title and summary of a Guide per language; the body lives in its TEXT blocks
CREATE TABLE guide_translations (
    id            uuid NOT NULL,
    project_id    uuid NOT NULL,
    guide_id      uuid NOT NULL,
    language_code text NOT NULL,
    title         text,
    summary       text,
    provenance    jsonb,
    CONSTRAINT pk_guide_translations PRIMARY KEY (id),
    CONSTRAINT fk_guide_translations_guide_same_project FOREIGN KEY (project_id, guide_id)
        REFERENCES guides (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_guide_translations_language UNIQUE (project_id, guide_id, language_code)
);
--rollback DROP TABLE guide_translations;

--changeset iguide:0020-create-guide-blocks
--comment A Guide block: TEXT (rich text per language) or REFERENCE to a Resource of the same Project
CREATE TABLE guide_blocks (
    id            uuid    NOT NULL,
    project_id    uuid    NOT NULL,
    guide_id      uuid    NOT NULL,
    block_type    text    NOT NULL,
    resource_type text,
    resource_id   uuid,
    sort_order    integer NOT NULL,
    configuration jsonb   NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_guide_blocks PRIMARY KEY (id),
    CONSTRAINT uq_guide_blocks_project_id_id UNIQUE (project_id, id),
    CONSTRAINT fk_guide_blocks_guide_same_project FOREIGN KEY (project_id, guide_id)
        REFERENCES guides (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_guide_blocks_order UNIQUE (project_id, guide_id, sort_order) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_guide_blocks_type CHECK (block_type IN ('TEXT', 'REFERENCE')),
    CONSTRAINT ck_guide_blocks_resource_type CHECK (resource_type IS NULL
        OR resource_type IN ('POI', 'ROUTE', 'PLACE', 'PAGE', 'EVENT')),
    CONSTRAINT ck_guide_blocks_shape CHECK (
        (block_type = 'TEXT' AND resource_type IS NULL AND resource_id IS NULL)
        OR (block_type = 'REFERENCE' AND resource_type IS NOT NULL AND resource_id IS NOT NULL)),
    CONSTRAINT ck_guide_blocks_sort_order CHECK (sort_order >= 0)
);

CREATE INDEX idx_guide_blocks_target ON guide_blocks (project_id, resource_type, resource_id);

COMMENT ON COLUMN guide_blocks.resource_id IS
    'Polymorphic: same-Project existence and status are checked by the service (06 section 5).';
--rollback DROP TABLE guide_blocks;

--changeset iguide:0021-create-guide-block-translations
--comment Text of a TEXT block, or caption of a REFERENCE block, per language
CREATE TABLE guide_block_translations (
    id             uuid NOT NULL,
    project_id     uuid NOT NULL,
    guide_block_id uuid NOT NULL,
    language_code  text NOT NULL,
    text           jsonb,
    caption        text,
    provenance     jsonb,
    CONSTRAINT pk_guide_block_translations PRIMARY KEY (id),
    CONSTRAINT fk_guide_block_translations_block_same_project FOREIGN KEY (project_id, guide_block_id)
        REFERENCES guide_blocks (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_guide_block_translations_language UNIQUE (project_id, guide_block_id, language_code)
);

COMMENT ON COLUMN guide_block_translations.text IS
    'Rich text document {"blocks": [{"type": "paragraph"|"heading", ...}]} (DECISIONS.md D-022).';
--rollback DROP TABLE guide_block_translations;

--changeset iguide:0022-create-events
--comment Event: a dated happening, optionally at a Place, a Point of Interest, or along a Route [ARC_3.3.4]
CREATE TABLE events (
    id                 uuid        NOT NULL,
    project_id         uuid        NOT NULL,
    slug               text        NOT NULL,
    status             text        NOT NULL,
    place_id           uuid,
    poi_id             uuid,
    route_id           uuid,
    booking_url        text,
    internal_name      text,
    featured           boolean     NOT NULL DEFAULT false,
    sort_order         integer     NOT NULL,
    configuration      jsonb       NOT NULL DEFAULT '{}'::jsonb,
    archived_at        timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    created_by_subject text        NOT NULL,
    updated_by_subject text        NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT fk_events_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_events_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_events_project_slug UNIQUE (project_id, slug),
    CONSTRAINT fk_events_place_same_project FOREIGN KEY (project_id, place_id) REFERENCES places (project_id, id),
    CONSTRAINT fk_events_poi_same_project FOREIGN KEY (project_id, poi_id)
        REFERENCES points_of_interest (project_id, id),
    CONSTRAINT fk_events_route_same_project FOREIGN KEY (project_id, route_id) REFERENCES routes (project_id, id),
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_events_booking_url CHECK (booking_url IS NULL OR length(booking_url) <= 2048)
);

CREATE INDEX idx_events_project_status_sort ON events (project_id, status, sort_order);
CREATE INDEX idx_events_project_place ON events (project_id, place_id);
CREATE INDEX idx_events_project_poi ON events (project_id, poi_id);
CREATE INDEX idx_events_project_route ON events (project_id, route_id);
--rollback DROP TABLE events;

--changeset iguide:0023-create-event-translations
--comment Visitor-facing text of an Event, one row per language
CREATE TABLE event_translations (
    id            uuid NOT NULL,
    project_id    uuid NOT NULL,
    event_id      uuid NOT NULL,
    language_code text NOT NULL,
    title         text,
    summary       text,
    description   text,
    location_text text,
    provenance    jsonb,
    CONSTRAINT pk_event_translations PRIMARY KEY (id),
    CONSTRAINT fk_event_translations_event_same_project FOREIGN KEY (project_id, event_id)
        REFERENCES events (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_event_translations_language UNIQUE (project_id, event_id, language_code)
);
--rollback DROP TABLE event_translations;

--changeset iguide:0024-create-event-occurrences
--comment One time an Event takes place; instants, rendered in the Project timezone (03 section 15)
CREATE TABLE event_occurrences (
    id         uuid        NOT NULL,
    project_id uuid        NOT NULL,
    event_id   uuid        NOT NULL,
    starts_at  timestamptz NOT NULL,
    ends_at    timestamptz NOT NULL,
    status     text        NOT NULL,
    CONSTRAINT pk_event_occurrences PRIMARY KEY (id),
    CONSTRAINT uq_event_occurrences_project_id_id UNIQUE (project_id, id),
    CONSTRAINT fk_event_occurrences_event_same_project FOREIGN KEY (project_id, event_id)
        REFERENCES events (project_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_event_occurrences_status CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    CONSTRAINT ck_event_occurrences_range CHECK (ends_at > starts_at)
);

CREATE INDEX idx_event_occurrences_project_starts ON event_occurrences (project_id, starts_at);
CREATE INDEX idx_event_occurrences_event ON event_occurrences (project_id, event_id, starts_at);
--rollback DROP TABLE event_occurrences;

--changeset iguide:0025-create-categories
--comment Category: a two-level taxonomy of Points of Interest or of Routes, never both (03 section 10)
CREATE TABLE categories (
    id                 uuid        NOT NULL,
    project_id         uuid        NOT NULL,
    parent_category_id uuid,
    scope              text        NOT NULL,
    code               text        NOT NULL,
    icon               text,
    status             text        NOT NULL,
    internal_name      text,
    presentation       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sort_order         integer     NOT NULL,
    archived_at        timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    created_by_subject text        NOT NULL,
    updated_by_subject text        NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT fk_categories_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_categories_project_id_id UNIQUE (project_id, id),
    CONSTRAINT uq_categories_project_code UNIQUE (project_id, code),
    CONSTRAINT fk_categories_parent_same_project FOREIGN KEY (project_id, parent_category_id)
        REFERENCES categories (project_id, id),
    CONSTRAINT ck_categories_not_own_parent CHECK (parent_category_id IS NULL OR parent_category_id <> id),
    CONSTRAINT ck_categories_scope CHECK (scope IN ('POI', 'ROUTE')),
    CONSTRAINT ck_categories_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_categories_project_status_sort ON categories (project_id, status, sort_order);
CREATE INDEX idx_categories_project_parent ON categories (project_id, parent_category_id);

COMMENT ON COLUMN categories.sort_order IS
    'Lists sort and reorder Categories by it (06 section 16, 07 section 14); 03 section 10 omits it (DECISIONS.md D-024).';
--rollback DROP TABLE categories;

--changeset iguide:0026-create-category-translations
--comment Name and description of a Category, one row per language
CREATE TABLE category_translations (
    id            uuid NOT NULL,
    project_id    uuid NOT NULL,
    category_id   uuid NOT NULL,
    language_code text NOT NULL,
    name          text,
    description   text,
    provenance    jsonb,
    CONSTRAINT pk_category_translations PRIMARY KEY (id),
    CONSTRAINT fk_category_translations_category_same_project FOREIGN KEY (project_id, category_id)
        REFERENCES categories (project_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_category_translations_language UNIQUE (project_id, category_id, language_code)
);
--rollback DROP TABLE category_translations;

--changeset iguide:0027-create-resource-categories
--comment A Point of Interest or Route in a Category; at most one Category per Resource (DECISIONS.md D-024)
CREATE TABLE resource_categories (
    id            uuid    NOT NULL,
    project_id    uuid    NOT NULL,
    category_id   uuid    NOT NULL,
    resource_type text    NOT NULL,
    resource_id   uuid    NOT NULL,
    sort_order    integer,
    CONSTRAINT pk_resource_categories PRIMARY KEY (id),
    CONSTRAINT fk_resource_categories_category_same_project FOREIGN KEY (project_id, category_id)
        REFERENCES categories (project_id, id),
    CONSTRAINT uq_resource_categories_resource UNIQUE (project_id, resource_type, resource_id),
    CONSTRAINT ck_resource_categories_type CHECK (resource_type IN ('POI', 'ROUTE'))
);

CREATE INDEX idx_resource_categories_category ON resource_categories (project_id, category_id);

COMMENT ON COLUMN resource_categories.resource_id IS
    'Polymorphic: same-Project existence, status, and scope are checked by the service (06 sections 5 and 12).';
--rollback DROP TABLE resource_categories;

--changeset iguide:0031-floor-plan-asset-foreign-key
--comment The plan image becomes an Asset of the same Project (GAPS.md G-025); ids written before Assets existed are cleared
UPDATE floor_plans f SET asset_id = NULL
WHERE asset_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM assets a WHERE a.project_id = f.project_id AND a.id = f.asset_id);

ALTER TABLE floor_plans
    ADD CONSTRAINT fk_floor_plans_asset_same_project FOREIGN KEY (project_id, asset_id)
        REFERENCES assets (project_id, id);

CREATE INDEX idx_floor_plans_project_asset ON floor_plans (project_id, asset_id);

COMMENT ON COLUMN floor_plans.asset_id IS
    'The plan image, an IMAGE or FLOOR_PLAN Asset of the Project; required to activate.';
--rollback DROP INDEX idx_floor_plans_project_asset;
--rollback ALTER TABLE floor_plans DROP CONSTRAINT fk_floor_plans_asset_same_project;
--rollback COMMENT ON COLUMN floor_plans.asset_id IS 'The plan image. Nullable and without a foreign key until Assets exist (GAPS.md G-025); required to activate.';

--changeset iguide:0032-create-interaction-triggers
--comment Interaction Trigger (03 section 14): a QR code, beacon, or geo-proximity area that opens its target Resource
CREATE TABLE interaction_triggers (
    id                   uuid        NOT NULL,
    project_id           uuid        NOT NULL,
    trigger_type         text        NOT NULL,
    target_resource_type text        NOT NULL,
    target_resource_id   uuid        NOT NULL,
    code                 text,
    supported_surfaces   jsonb,
    status               text        NOT NULL,
    configuration        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sort_order           integer     NOT NULL,
    archived_at          timestamptz,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    created_by_subject   text        NOT NULL,
    updated_by_subject   text        NOT NULL,
    version              bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_interaction_triggers PRIMARY KEY (id),
    CONSTRAINT fk_interaction_triggers_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_interaction_triggers_project_id_id UNIQUE (project_id, id),
    CONSTRAINT ck_interaction_triggers_type CHECK (trigger_type IN ('MANUAL', 'QR_CODE', 'GEO_PROXIMITY', 'BEACON',
        'CAMERA_RECOGNITION')),
    CONSTRAINT ck_interaction_triggers_target_type CHECK (target_resource_type IN ('POI')),
    CONSTRAINT ck_interaction_triggers_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_interaction_triggers_code_only_qr CHECK (code IS NULL OR trigger_type = 'QR_CODE')
);

-- 06 section 12: a QR code is unique per Project where present.
CREATE UNIQUE INDEX uq_interaction_triggers_project_code ON interaction_triggers (project_id, code) WHERE code IS NOT NULL;
CREATE INDEX idx_interaction_triggers_target ON interaction_triggers (project_id, target_resource_type, target_resource_id, sort_order);

COMMENT ON COLUMN interaction_triggers.target_resource_type IS
    'Points of Interest only so far; other kinds join with their editors (DECISIONS.md D-026).';
COMMENT ON COLUMN interaction_triggers.configuration IS
    'BEACON: uuid, major, minor, proximityMeters, cooldownSeconds. GEO_PROXIMITY: latitude, longitude, radiusMeters, cooldownSeconds.';
COMMENT ON COLUMN interaction_triggers.sort_order IS 'The order of the triggers of one Resource in its editor.';
--rollback DROP TABLE interaction_triggers;
