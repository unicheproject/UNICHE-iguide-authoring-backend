# DECISIONS

Append-only. An entry is never edited or removed; a later entry supersedes it by id. Types:
`implementation`, `spec-interpretation`, `adr`. ADR entries carry `Owner approval`.

## D-001 (2026-10-02) — Bootstrap regime

- Type: implementation
- Decision: the living documents (AGENTS.md, CLAUDE.md, PLAN.md, DECISIONS.md, GAPS.md, QUESTIONS.md,
  TRACEABILITY.md, QUICKSTART.md, Makefile, SESSION_BOOTSTRAP_PROMPT.md) are created from
  `../bootstrap-backend-prompt.md`. The bootstrap session changed no source code, build file, test, or
  changeset. The owner instructed two configuration and documentation corrections in it: the Catalogue
  URL in `.env.example` and `README.md` (D-008).
- Why: every later session continues from one short prompt against truthful documents.
- Alternatives: fixing gaps during the bootstrap; rejected, because findings must first be recorded and
  prioritized.
- Affected specs: none.

## D-002 (2026-10-02) — Specs are read-only from this repository

- Type: implementation
- Decision: no session working in this repository creates, edits, or deletes files in
  `../uniche_iguide_specs`, whatever the specs' own `CLAUDE.md` says. Proposed changes are QUESTIONS.md
  `spec-change-request` entries. A spec edit happens only on an explicit owner instruction in the
  session, and is recorded here. Exception granted 2026-10-02: the Catalogue URL in `10` §4 and specs
  `CLAUDE.md` (D-008).
- Why: the specs are the design of record shared by the backend and the CMS; drift must be deliberate.
- Alternatives: editing specs alongside code; rejected (workspace `CLAUDE.md`).
- Affected specs: all.

## D-003 (2026-10-02) — Specs baseline

- Type: implementation
- Decision: baseline commit `1c982b656bef9fd0470485f02401abdae56e0f61` of 2026-10-01 17:54 +0300
  ("Adopt the MVP scope review and align specs with the CMS prototype"), plus the uncommitted owner
  edit of 2026-10-02 (D-008). Once the owner commits that edit, the next session moves the baseline to
  that commit with a new entry.
- Why: the backend code (2026-07-30) predates this baseline; every gap in GAPS.md is measured against it.
- Alternatives: none.
- Affected specs: all.

## D-004 (2026-10-02) — OpenAPI ownership, path, and drift check

- Type: implementation
- Decision: this backend owns the authoring and runtime API contract. springdoc generates it; it is
  committed as `openapi/openapi.yaml`. A test running in `./mvnw verify` renders `/iguide/api-docs.yaml`
  from the application context and fails on any difference from the committed file; regeneration
  overwrites the file through one documented command. The frontend consumes that file. First built by
  PLAN.md M0a.
- Why: `10` §8 requires the document to be generated at build time and committed with each release
  [ARC_4.8]; a test catches a route change without a regenerated contract without an extra plugin or a
  running server.
- Alternatives: `springdoc-openapi-maven-plugin` with a started application (needs a database and IdP
  at build time); a CI-only diff (misses local builds).
- Affected specs: `10` §8, `09` §18.

## D-005 (2026-10-02) — `RestClient` for the Catalogue client (found in place)

- Type: spec-interpretation
- Decision: the Catalogue adapter uses Spring `RestClient` on `JdkClientHttpRequestFactory` with explicit
  connect and read timeouts and one default status handler. `07` §5 "Use `WebClient` or Spring HTTP
  Interface clients" is read as a suggestion of a modern Spring client; its requirements (timeouts,
  client-wide mapping, token forwarding, no local fallback) are what bind. Spec text proposed in
  QUESTIONS.md Q-008.
- Why: synchronous servlet stack; `RestClient` is the blocking successor of `RestTemplate` and does not
  pull in WebFlux.
- Alternatives: `WebClient` (reactive dependency for blocking calls), HTTP Interface over `RestClient`
  (possible later without changing the mapping).
- Affected specs: `07` §5.

## D-006 (2026-10-02) — Build and container tooling (found in place)

- Type: implementation
- Decision, as found in commits `0d3006e` to `9323499`:
  - Lombok (optional, 3 files);
  - Testcontainers `1.21.3` overriding the parent BOM, with surefire `api.version=1.41`, because the
    managed version cannot negotiate with Docker Engine 29;
  - springdoc `2.8.6`; Maven wrapper `3.9.9`;
  - images `maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre-alpine`, `postgres:16-alpine`;
  - Dockerfile launches the extracted thin jar with `java -jar application.jar` instead of
    `JarLauncher`, because Boot 3.4 `jarmode=tools` extraction leaves the loader layer empty;
    divergence from `10` §6 item 5, proposed in QUESTIONS.md Q-007;
  - required platform properties checked by an `EnvironmentPostProcessor`
    (`PlatformConfigurationCheck`) so a missing issuer fails before the datasource starts;
  - Liquibase formatted SQL, changeset ids `iguide:NNNN-slug`, one file per migration group.
- Why: recorded retroactively; the specs leave these open or the code had a concrete reason.
- Alternatives: none evaluated.
- Affected specs: `10` §1, §5, §6.

## D-007 (2026-10-02) — Test doubles for the platform (found in place)

- Type: implementation
- Decision: security tests use the production `SecurityConfig` against `TestIdp`, an in-process stub
  realm serving discovery and JWKS and minting RS256 tokens; Catalogue tests use `StubCatalogueServer`,
  a real socket, so timeouts and forwarded headers are exercised; persistence tests extend
  `PostgresIntegrationTest` (Testcontainers, Liquibase-created schema). The unroutable
  `uniche.catalogue.base-url=http://localhost:1` in the test profile makes a forgotten stub fail loudly.
- Why: a decoder or client replaced by a test bean proves little about the production path.
- Alternatives: mocked `JwtDecoder` and request factory; rejected.
- Affected specs: `07` §17.

## D-008 (2026-10-02) — Catalogue URL is `catalogue.uniche-eccch.eu`

- Type: implementation
- Decision: the Catalogue base URL is `https://catalogue.uniche-eccch.eu`; `https://platform.uniche-eccch.eu`
  is the Portal SPA. On owner instruction of 2026-10-02 the URL was corrected in `.env.example`,
  `README.md`, specs `10` §4, and specs `CLAUDE.md` (exception to D-002; specs edit uncommitted). Resolves QUESTIONS.md Q-010.
- Why: verified 2026-10-02: the Portal host answers `GET /api/v1/me/authorization` with its HTML shell
  and `200`; the Catalogue host serves `/v3/api-docs` ("UNICHE Org & Project Catalogue API") and answered
  authenticated calls from this backend end to end (TRACEABILITY.md, live integration run).
- Alternatives: none.
- Affected specs: `10` §4, specs `CLAUDE.md` §External references.

## D-009 (2026-10-02) — Tool scoping: only `iguide` Projects exist for this backend

- Type: spec-interpretation
- Decision: the Project access check treats a Catalogue `200` whose `tool.slug` differs from
  `uniche.tool.slug` exactly like a `404`: the caller gets an opaque `404`, no companion row is written,
  and no existing row is touched. Organization and deleted listings keep only Projects of this tool.
  Implemented by PLAN.md M1c; spec text proposed in QUESTIONS.md Q-004. Owner agreed to the slice on
  2026-10-02.
- Why: the Catalogue does not filter by tool (`UNICHEcatalogue` `ProjectService`, verified live: the
  Test Museum organization lists 11 `museotekbox` and 2 `media-editor` Projects). On 2026-10-02 this
  backend created a companion row for a MuseotekBox Project, which breaks the one-Project-one-Application
  model (`01` §5) and lets this tool author into another tool's Project. The Media Editor reference
  filters the same way.
- Alternatives: `403` for a foreign tool (would confirm existence, contrary to `07` §15); no check
  (current defect).
- Affected specs: `02` §2, §11, §12; `07` §6, §15.

## D-010 (2026-10-02) — Local composition: backend services only, frontends as opt-in profiles

- Type: spec-interpretation
- Decision: this repository's `compose.yaml` owns `app` and `postgres`, and `postgres` publishes its port
  on the host so the application also runs from Maven (closes G-012 with M0c). The CMS runs from its own
  repository (Vite dev server on `http://localhost:5173`, the `iguide-web` redirect). `cms`, `runtime`,
  and `vibe-mock` enter this compose file as opt-in Compose profiles, built from the sibling repository
  (`../uniche_iguide_frontend`, and the runtime repository once it exists), when that repository ships a
  Dockerfile; `runtime` and `vibe-mock` arrive with A0. `08` Milestone 0 "four-service local composition"
  is read as the target topology, not a gate for backend done of Milestone 0. Resolves QUESTIONS.md Q-001;
  owner left the choice open on 2026-10-02.
- Why: no CMS or runtime image exists yet; a profile keeps `docker compose up` working for backend work
  while giving one command for the full stack later; no registry is needed.
- Alternatives: published images (needs a registry and release flow); each repository its own compose
  only (no single command for preview, contrary to `10` §6).
- Affected specs: `10` §6, `08` Milestone 0.

## D-011 (2026-10-02) — Deleted Projects, restore, and invitations deferred for the MVP

- Type: spec-interpretation
- Decision: the CMS MVP offers no deleted-Projects list, no restore action, and no invitation screen;
  these are done in the Platform Portal or the Catalogue's own interface. The existing backend
  passthroughs `GET /api/v1/organizations/{orgId}/projects/deleted` and
  `POST /api/v1/projects/{projectId}/restore` stay as they are (platform admin only, Catalogue-enforced)
  and receive no further work beyond tool scoping (M1c). No invitation passthrough is built; the
  `[ARC_4.3 divergence]` of `02` §14 stands for the MVP. Resolves QUESTIONS.md Q-005 and Q-006 for the
  MVP; owner decision 2026-10-02.
- Why: the Catalogue restricts deleted listing and restore to platform admins, and the platform already
  provides these and invitations; none is needed for the iGuide MVP.
- Alternatives: admin-only screens in the CMS; an invitation passthrough (`09` §13).
- Affected specs: `12` §3, §6; `02` §11, §14; `09` §13.

## D-012 (2026-10-02) — Request id sent to the Catalogue under both headers

- Type: adr
- Owner approval: granted 2026-10-02
- Decision: every Catalogue call carries the current request id as `X-UNICHE-Request-Id` and, with the
  same value, as `X-Correlation-Id`, the only header the Catalogue reads today. The second header is
  removed once the Catalogue adopts `X-UNICHE-Request-Id`. Implemented in M0a. Resolves QUESTIONS.md
  Q-003; the spec text proposed there stays a pending spec change.
- Why: without `X-Correlation-Id` no Catalogue log line can be joined to an iGuide request (verified live
  2026-10-02); the platform header keeps `07` §5 [ARC_7.9] satisfied.
- Alternatives: only `X-UNICHE-Request-Id` until the Catalogue changes (no correlation meanwhile).
- Affected specs: `07` §5, `10` §8.

## D-013 (2026-10-02) — Row-Level Security: ordering and database roles

- Type: spec-interpretation
- Decision:
  - the access check runs the companion upsert in one transaction that first executes
    `SET LOCAL app.project_id` with the Project id the Catalogue has just confirmed; `projects` keeps the
    single policy on `id` of `06` §13, and no role bypasses it;
  - the migration role (table owner, Liquibase) and the application role (owns nothing, subject to
    RLS) are created by a Liquibase changeset in group 1 for local development and Testcontainers; tests
    connect as the application role;
  - in staging and production the roles are provisioned by operations and their credentials are secrets
    (`spring.liquibase.user`/`password`, `spring.datasource.username`/`password`); the changeset does not
    create them there (Liquibase context). The cross-Project job question of `09` §26 stays open until
    Milestone 12.
- Resolves QUESTIONS.md Q-002; owner agreed 2026-10-02. Implemented in M1b.
- Why: keeps the `07` §3 flow without a privileged path; creating roles needs a superuser, which the
  application must not hold in production.
- Alternatives: a separate bypass role for the upsert; roles created by the application in every
  environment.
- Affected specs: `06` §13, `07` §3–4, `10` §3, `09` §26.

## D-014 (2026-10-02) — Second owner-instructed spec amendment

- Type: implementation
- Decision: on owner instruction of 2026-10-02 ("change the specs project if needed"), exception to D-002,
  the specs were amended, uncommitted:
  - `02` §2 steps 4–5 and a new paragraph: a Project of another authoring tool is answered `404`
    (QUESTIONS.md Q-004, D-009);
  - `02` §11: `GET /api/v1/organizations` passthrough; listings keep only this tool's Projects; no deleted
    list or restore in the CMS (D-011);
  - `07` §5: `RestClient` (Q-008, D-005) and the second correlation header (Q-003, D-012);
  - `07` §14: `/api/v1/organizations`;
  - `08` Milestone 1: the organizations passthrough and a tool-scoping acceptance criterion;
  - `10` §6 item 5: `java -jar` entrypoint (Q-007, D-006);
  - `12` §3, §6, §7: picker without deleted list and restore; endpoint map.
- Resolves QUESTIONS.md Q-004, Q-007, Q-008. GAPS.md G-014 closes: code and spec now agree.
- Why: the platform integration is the first goal (D-015) and these passages contradicted the verified
  Catalogue behavior or the code that works.
- Alternatives: keep them as pending spec-change-requests.
- Affected specs: `02`, `07`, `08`, `10`, `12`.

## D-015 (2026-10-02) — First goal: Keycloak and Catalogue integration end to end on the dev box

- Type: implementation
- Decision: owner priority of 2026-10-02. Before RLS, mocks, and Milestone 2, the CMS and this backend work
  together on a developer machine against the live Keycloak realm (`iguide-web`) and the live Catalogue:
  sign-in, `/me/authorization`, organization and Project picker, Project creation step 1, Project open,
  rename, delete, and the error envelopes. Backend `Now` is M0a, M1a, M1c (with the organizations
  passthrough); `Next` starts with M0c so the app also runs from Maven. The matching frontend plan is
  `../uniche_iguide_frontend/PLAN.md` (C0a to C0c). RLS (M1b) follows immediately after.
- Why: every later milestone and CMS step stands on this path; it is the cheapest point to find
  platform mismatches, as the 2026-10-02 live run showed.
- Alternatives: the `08` order (RLS within Milestone 1 before the CMS).
- Affected specs: `08` Milestone 0 and 1, §Companion repositories C0.

## D-016 (2026-10-02) — Specs baseline moved to `a792ede`

- Type: implementation
- Decision: the specs baseline is `a792ede3086068f24ffb2c07f608215bee4ec516` (2026-10-02 15:36 +0300),
  the commit of the owner-instructed edits of D-008 and D-014 plus one more: Categories and Pages default to
  `sortOrder asc` in `07` §14 and `12` §3, as `06` §16 already listed (frontend D-011). Supersedes the
  baseline of D-003.
- Why: the owner committed the edits on 2026-10-02.
- Alternatives: none.
- Affected specs: `02`, `07`, `08`, `10`, `12`, specs `CLAUDE.md`; Milestone 4 default sort.

## D-017 (2026-10-13) — `/me/authorization` returns the Catalogue body through a typed record

- Type: spec-interpretation
- Decision: `GET /api/v1/me/authorization` returns `CatalogueMeAuthorizationDto` as the Catalogue sent it:
  `subject`, `platformAdmin`, `managedOrganisations`, `projectMemberships[{projectId, orgId, role}]`,
  `etag`, `ttlSeconds`, with the Catalogue's names. Nothing is cached and no `If-None-Match` is sent.
  The body passes through a typed record rather than as raw bytes, so fields the Catalogue adds later are
  dropped until the record names them. Supersedes the separate `MeAuthorizationResponse` shape.
- Why: `02` §10 asks for the Catalogue body unchanged; a typed record keeps a schema in the OpenAPI
  contract for the CMS's generated client (C0b), which a raw passthrough would reduce to a free-form object.
- Alternatives: a raw `JsonNode` passthrough (exact, but untyped in the contract).
- Affected specs: `02` §10, `07` §2.

## D-018 (2026-10-14) — Languages, Places, Maps, and Floor Plans ported from MuseotekBox ahead of order

- Type: implementation
- Decision: on owner instruction, the Places work built in `UNICHE-museotekbox-authoring-backend` is
  ported here before M0a, M1b, and M2 are done, with Project Languages (Places need a default language),
  Geographic Maps, and Floor Plans (PLAN.md M4p). MuseotekBox supplies code; the specs win where they
  differ: `environment` instead of `setting`; the Place link lives on the map or plan (`placeId`), with
  no `spatial-link` endpoint; several Floor Plans per Place in `sortOrder`; no floor plan connections;
  map `visibility` and `visitorFilterEnabled` columns; placements as child collections
  (`.../maps/{id}/resources`, `.../floor-plans/{id}/positions`); paged lists. `FloorPlan.assetId` is
  nullable without a foreign key until Assets exist, and a plan without an image cannot be activated.
  Missing foundations are recorded as gaps (G-022 to G-029), not built.
- Why: the owner moved backend work to this repository on 2026-10-08 and asked for the existing work to
  follow, Places first, checked against the specs (2026-10-14).
- Alternatives: M0a and M1b first (PLAN.md order); Assets first so floor plans keep a required image.
- Affected specs: `03` §3, §5, §7, §8; `08` Milestones 4 and 5.

## D-019 (2026-10-14) — Interpretations for Places, Maps, Floor Plans, and Languages

- Type: spec-interpretation
- Decision:
  - only `DRAFT` Places, maps, and plans are edited, placements included (`422 RESOURCE_NOT_EDITABLE`
    otherwise), until working copies exist (M8): AGENTS.md §4 rule 13 forbids live edits of `ACTIVE`;
  - a Place is in use while a non-archived map or plan links it or places it; sub-Places do not block
    its archival (`03` §1 lists neither them nor POIs); archiving a map or plan clears its `placeId`;
  - a map placement stores its own `latitude` and `longitude` (`03` §7 names only `presentation`);
    placement `resource_type` admits `POI` and `PLACE` in the schema, the service only `PLACE`;
  - a Place's `environment` may change on a `DRAFT` Place only while no map or plan links it;
  - Place translations are written through the update body, keyed by language code (the conflict paths
    of `07` §15 name `translations.en.summary`); `translations/{lang}` comes later (G-026);
  - a Place slug is generated from the default-language title when the request has none, accents
    dropped, `-2`, `-3` appended on collision; non-Latin titles yield `place`;
  - `ProjectLanguage.isDefault` is the only record of the default language until ProjectSettings exist
    (`03` §2–3 store it twice); removing a language disables it and keeps its translations;
  - maps list by `updatedAt desc`, Places and plans by `sortOrder asc`; reorder writes `sortOrder`
    without touching `version`.
- Why: each point is open or silent in the specs (`03` §1, §3, §5, §7; `07` §14), and work proceeds.
- Alternatives: per point, see the spec references.
- Affected specs: `03` §1, §3, §5, §7, §8; `07` §14–15; `12` §4.

## D-020 (2026-10-14) — Points of Interest ported from MuseotekBox, and their interpretations

- Type: spec-interpretation
- Decision: on owner instruction Points of Interest follow Places (PLAN.md M4q), with the spec's
  `PoiTranslation` only: no `seoTitle`/`seoDescription` (owner decision, QUESTIONS.md Q-012), no
  `categoryId` or media until Categories and Assets exist (G-029, G-025). Interpretations:
  - `code` is optional, letters, digits, and single hyphens, at most 32 characters, kept as typed and
    compared case-sensitively; unique per Project where set (`06` §12); not checked against
    Interaction Trigger codes, which do not exist yet;
  - `placeId` points at any non-archived Place of the Project, whatever its `environment` (`03` §6 is
    silent); a POI inside a Place does not block the Place's archival (as sub-Places, D-019);
  - a POI is in use while a non-archived map or floor plan places it; Routes, Guides, Events, and
    Triggers add their references with their milestones;
  - the list's `q` matches the title in any language or the exact `code` (`13` §3 ranks an exact code
    first), and `placeId` filters by Place;
  - placements and positions now accept `POI` as well as `PLACE`.
- Why: `03` §6 and `07` §14 leave these open; MuseotekBox's choices fit them.
- Alternatives: a numeric-only code (`13` §2 says "numeric or alphanumeric").
- Affected specs: `03` §1, §6, §7, §8; `06` §12; `07` §14.

## D-021 (2026-10-15) — Routes ported from MuseotekBox, and their interpretations

- Type: spec-interpretation
- Decision: on owner instruction Routes and Route Stops follow POIs (PLAN.md M5r), on the spec's model
  (`03` §9): `travelMode` (not MuseotekBox's `mode`), difficulty `HARD` (not `DEMANDING`), no optional
  stop flag and no per-stop duration, no SEO texts (Q-012), stops as the child collection of `07` §14.
  Categories and media wait for G-029 and G-025. Interpretations:
  - a Point of Interest is at most one stop of a Route (`UNIQUE (project_id, route_id, poi_id)`); the
    spec is silent and the CMS already prevents it;
  - stop positions are 0-based and contiguous: a new stop goes last, a removed one closes the gap,
    `stops:order` takes every stop id; the runtime's 1-based `n` is position + 1;
  - `distanceMeters` is the author's override and stays null until set; responses also carry
    `computedDistanceMeters`, the great-circle length through the stops' POI coordinates in order,
    skipping stops without coordinates, closing the loop when `loop` and three or more points;
  - a stop's POI must be in the Project and not archived; a DRAFT POI is allowed, but activation needs
    the default-language title and at least one stop whose POI is ACTIVE (`03` §1);
  - stops of an ACTIVE Route are not edited until working copies exist (D-019); a stop write updates the
    Route's `updatedAt`;
  - nothing refers to a Route until Guides, Events, Navigation, and Triggers exist; a non-archived
    Route with a stop on a POI blocks that POI's deactivation and archival.
- Why: `03` §9 and `07` §14 leave these open; MuseotekBox's choices fit them where the spec is silent.
- Alternatives: stops embedded in the Route update body, as MuseotekBox and the CMS do.
- Affected specs: `03` §1, §9; `06` §3, §12; `07` §14; `11` §9.

## D-022 (2026-10-15) — Guides on the spec's block model, bridged for the current CMS editor

- Type: spec-interpretation
- Decision: Guides follow `03` §11 — `GuideTranslation` (title, summary), ordered `GuideBlock`s
  (`TEXT` or `REFERENCE`) with `GuideBlockTranslation` (text or caption), and the `.../blocks` child
  collection of `07` §14 — not MuseotekBox's per-language `body` and `GuideItem` list, which the spec
  supersedes. The owner chose (2026-10-15) to keep the current CMS editor through an adapter: each
  language's story is the text of one TEXT block placed first, related items are REFERENCE blocks after it.
  Interpretations:
  - rich text is a JSON document `{"blocks": [{"type": "paragraph" | "heading", "text", "level"}]}`, the
    shape the CMS already edits (`03` §11 says "the same format Page components use", which is not yet
    defined): at most 500 nodes, 20 000 characters each, heading level 2 or 3, empty nodes dropped;
    `image` nodes are refused until Assets can hold their INLINE bindings (G-025);
  - REFERENCE targets are POIs, Routes, and Places of the Project that are not archived; Pages and Events
    are refused until they exist; the same target may appear in several blocks (the spec is silent);
  - a block keeps its type; TEXT blocks take no caption and REFERENCE blocks no text;
  - activation needs, in the default language, the title, at least one block, and text on every TEXT block;
  - a non-archived Guide referencing a POI, Route, or Place blocks that Resource's deactivation and
    archival; nothing refers to a Guide until Navigation and Triggers exist;
  - no SEO texts on Guides (Q-012).
- Why: owner decision on the model; the remaining points are open in `03` §11 and `07` §14.
- Alternatives: MuseotekBox's model as is; a block editor in the CMS now.
- Affected specs: `03` §1, §4, §11; `07` §14; `12` §3.

## D-023 (2026-10-15) — Events ported from MuseotekBox, and their interpretations

- Type: spec-interpretation
- Decision: on owner instruction Events follow Guides (PLAN.md M9e), on `03` §15: Event, EventTranslation
  (title, summary, description, locationText), and EventOccurrence (`startsAt`, `endsAt` instants,
  `SCHEDULED` | `CANCELLED`), with occurrences as the child collection `.../events/{id}/occurrences` of
  `07` §14 rather than part of the Event body as in MuseotekBox; the CMS adapter syncs them. Interpretations:
  - the tables live in migration group 4 (`06` §15 names no group for Events);
  - links to a Place, POI, or Route are optional, must be in the Project, and not archived; a
    non-archived Event keeps them in use (`03` §1); Guides may reference Events, which keeps the Event in use;
  - an occurrence ends after it starts and lasts at most 366 days; at most 500 per Event; occurrences are
    ordered by `startsAt`, so their collection has no `:order` (`07` §14 lists none for them);
  - activation needs only the default-language title; an Event without occurrences may be ACTIVE;
  - the booking URL, when set, is an http or https address with a host, at most 2048 characters;
  - calendar queries (`08` Milestone 9) are `GET .../events/occurrences?from&to`, at most 400 days,
    returning the overlapping occurrences of every Event of the Project in start order;
  - "every occurrence of a non-archived Event" (`11` §3) is read as of every ACTIVE Event, as `03` §15
    and `03` §1 say, for the manifest of Milestone 8;
  - no timezone is stored and the `EVENTS` capability is not checked until ProjectSettings exist (G-024).
- Why: `03` §15 and `07` §14 leave these open; MuseotekBox's limits fit them.
- Alternatives: occurrences in the Event update body, as MuseotekBox and the CMS do.
- Affected specs: `03` §1, §15; `06` §15; `07` §14; `08` Milestone 9; `11` §3.

## D-024 (2026-10-15) — Categories ported from MuseotekBox, on the spec's ResourceCategory rows

- Type: spec-interpretation
- Decision: on owner instruction Categories follow Events (PLAN.md M4c), on `03` §10: Category (scope
  `POI` | `ROUTE`, two levels, `code`, `icon`), CategoryTranslation (name, description), and
  `ResourceCategory` rows managed at `.../pois/{id}/categories` and `.../routes/{id}/categories` (`07`
  §14), not MuseotekBox's `categoryId` column; the CMS adapter maps its single select onto them.
  Interpretations:
  - a Point of Interest or Route is in at most one Category (`UNIQUE (project_id, resource_type,
    resource_id)`): `03` §9 and `12` §3 say "the Category", although the row model would allow more;
    a second binding is `409 DUPLICATE_VALUE`;
  - Categories carry `sort_order`, which `03` §10 omits although `06` §16 and `07` §14 sort and reorder
    them by it (Q-013);
  - `icon` is one of the 20 keys the CMS draws (monument, building, statue, vase, church, castle, tree,
    mountain, beach, view, food, shop, info, photo, water, harbour, factory, trail, family, bike); the
    spec names no keys (Q-013);
  - `code` follows the slug format, is unique per Project, and is generated from the name when absent;
  - the parent is a top-level Category of the same scope, in the Project, not archived; a Category with
    subcategories stays top-level; a scope change is refused while Resources are bound, and moves the
    subcategories along;
  - activation needs the default-language name and an ACTIVE parent; restore needs a parent that is not
    archived; deactivation and archival are refused while non-archived subcategories or non-archived
    bound POIs or Routes exist; map `hiddenCategoryIds` are not references (`03` §1 does not list them);
  - a binding needs a Category of the Resource's scope that is not archived, and a DRAFT Resource
    (working copies, D-019); POI and Route reads carry `categoryIds`, as the manifest will (`11` §3);
  - map `visibility.POI.hiddenCategoryIds` holds POI Categories and `ROUTE` Route Categories of the Project.
- Why: `03` §10 and `07` §14 leave these open or contradict each other; MuseotekBox's rules fit them.
- Alternatives: several Categories per Resource; a `categoryId` column as in MuseotekBox.
- Affected specs: `03` §1, §7, §9, §10; `06` §12, §16; `07` §14; `11` §3; `12` §3.

## D-025 (2026-10-16) — Assets ported from MuseotekBox, MinIO in the local composition, and media bindings

- Type: spec-interpretation and owner decision (MinIO, 2026-10-16)
- Decision: on owner instruction media follows Categories (PLAN.md M3), on `03` §4 and `07` §10:
  Asset, AssetTranslation (title, caption, alternativeText, credits, transcript, provenance), and
  `ResourceAsset` bindings managed at `.../{pois|routes|guides|events}/{id}/resource-assets` and
  `.../resource-assets:order` (`07` §14). Bytes go to S3-compatible storage through MuseotekBox's
  `S3AssetStorage` (path-style, bucket head request at startup, 15-minute signed URLs). Interpretations:
  - the owner chose a MinIO service in `compose.yaml` for development although `10` §7 says the local
    composition ships no storage service; the official images are no longer published, so it is
    Bitnami's frozen MinIO build (`bitnamilegacy/minio`), which creates the private bucket itself;
  - `iguide.assets.s3.public-endpoint` (optional, not in the `10` §3 contract) names the host signed URLs
    are made for, because inside compose the application reaches MinIO at `minio:9000` and the browser at
    `localhost:9000` (Q-014);
  - endpoints beyond `07` §14, ported from MuseotekBox because the CMS uses them: `POST .../assets/{id}/file`
    (replace the file of a DRAFT Asset with one of the same type), `GET .../assets/{id}/usages` (where
    used, with the role in each Resource, `FLOOR_PLAN_IMAGE` for a plan), `GET .../assets/{id}/download`
    (a signed link with `Content-Disposition: attachment`) (Q-014); `PUT .../assets/{id}/translations/{lang}`
    is not offered, the Asset `PUT` carries the texts by language as the other kinds do;
  - an ACTIVE Asset stays editable in place (name, decorative flag, texts), unlike D-019: it is usually
    used by ACTIVE content, which blocks its deactivation, so missing alternative text could not be fixed
    until working copies exist; it keeps its default-language alternative text; only ARCHIVED is read-only;
  - the type follows the MIME type, then the extension; an upload may only ask for `FLOOR_PLAN`, for an
    image; activating a non-decorative IMAGE needs default-language `alternativeText`;
  - usages are `03` §4's examples plus `VIDEO` (the CMS video slot): one binding per Resource, usage, and
    language for HERO_IMAGE, THUMBNAIL, VIDEO, AUDIO_NARRATION, MODEL, MODEL_POSTER; accepted types as
    MuseotekBox (images for HERO, THUMBNAIL, GALLERY, MODEL_POSTER, INLINE; DOCUMENT or OTHER for
    ATTACHMENT); "a MODEL binding also binds a MODEL_POSTER" is enforced at bind time: the poster first,
    and it cannot be removed while the model is bound;
  - rich text embeds `{"type": "asset", "assetId"}` (`03` §4), replacing D-022's refused `image` node;
    every block write reconciles the Guide's INLINE rows over all its TEXT blocks and languages; INLINE
    rows are never posted, deleted, or ordered directly (`:order` lists the other bindings, INLINE rows
    stay after them);
  - bindings and their order change only on a DRAFT Resource (D-019); the Asset must be in the Project and
    not archived; Places, maps, and Floor Plans bind no media, a Floor Plan names its image directly
    (`IMAGE` or `FLOOR_PLAN`, not archived, its pixel size filling `width`/`height` when not sent);
  - a non-archived binding Resource or Floor Plan keeps an Asset in use (`RESOURCE_IN_USE`); POI, Route,
    Guide, and Event reads carry `media` in `sortOrder`, INLINE rows included;
  - upload limits are the `10` §3 values, `GET /api/v1/upload-limits` returns
    `{maxUploadBytes, perType}`; an upload above a limit is `413 ASSET_TOO_LARGE` naming the limit.
- Why: owner decision on local storage; `03` §4 and `07` §10, §14 leave the rest open.
- Alternatives: a development bucket from the owner (`10` §7 as written); the CMS's s3mock.
- Affected specs: `03` §1, §4, §8, §11; `07` §10, §14; `10` §3, §7; `12` §3.

## D-026 (2026-10-19) — Interaction Triggers for Points of Interest

- Type: spec-interpretation and owner decisions (2026-10-19)
- Decision: on owner instruction the "How visitors reach it" card of the POI editor gets a backend, on
  `03` §14: `interaction_triggers` (changeset 0032) and `.../triggers` endpoints of `07` §14 (list by
  target and status, get, create, update, status, `qr.png`). Interpretations:
  - authorable types are `QR_CODE`, `BEACON`, and `GEO_PROXIMITY` (owner); `MANUAL` needs no row because
    every Resource is reachable by browsing, and `CAMERA_RECOGNITION` is deferred (`08` deferred list),
    both `422 UNSUPPORTED_TYPE`; the CMS shows "AR scan target" greyed with that reason;
  - a `BEACON` is an iBeacon `uuid` / `major` / `minor` (owner; `09` §11 leaves the format open, Q-015)
    with `proximityMeters` (0.5–50, default 2) and `cooldownSeconds` (0–86400, default 300); the same
    identity twice among the Project's non-archived beacons is `409`;
  - a `GEO_PROXIMITY` area is `latitude`, `longitude`, `radiusMeters` (5–5000), and `cooldownSeconds`;
  - a `QR_CODE` code is Latin letters, digits, and single hyphens, at most 64, generated (8 characters)
    when absent, unique per Project including archived triggers so a printed code never changes target;
    only `QR_CODE` has a code; unknown configuration keys are refused;
  - `supportedSurfaces` is null or a subset of the surfaces able to run the type (`13` §1);
  - targets are Points of Interest of the Project that are not archived; other kinds follow with their
    editors; the type and target never change;
  - a trigger is edited in place while not archived, like an Asset (D-025): it is a mechanism of its
    target, whose own DRAFT rule still guards the editor; the CMS creates triggers and activates them at
    once and archives removed ones, so `?t=` keeps resolving (`11` §9);
  - a non-archived trigger keeps its POI in use (`03` §1 lists InteractionTrigger targets);
  - `qr.png` is `422 NO_PUBLIC_BASE_URL` until the `WEB` surface's `publicBaseUrl` exists (owner: code now,
    image later), as `07` §14 prescribes for a missing address;
  - triggers carry `sort_order`, the order in their target's editor (`03` §14 has none).
- Why: `03` §14, `09` §11, and `12` §3 leave these open; owner choices on types, beacon format, and QR.
- Alternatives: a free-text beacon identifier; a temporary public address for QR images.
- Affected specs: `03` §1, §14; `07` §14; `08` Milestone 5; `09` §11; `11` §9; `12` §3; `13` §1.

## D-027 (2026-10-20) — Working copies for the content kinds (M8w)

- Type: spec-interpretation and owner decision (2026-10-20: all eight content kinds, backend and CMS together)
- Decision: `resource_working_copies` (changeset 0033, group 8) and `GET`, `PUT`, `DELETE
  .../{kind}/{id}/working-copy` plus `POST .../working-copy:apply` for `places`, `pois`, `routes`, `guides`,
  `events`, `categories`, `maps`, and `floor-plans` (`03` §1, `06` §17, `07` §14). Interpretations:
  - the `PUT` body is `{baseVersion, workingCopyVersion, payload}`. On creation `baseVersion` must be the live
    row's version; on replacement `workingCopyVersion` must be the copy's version, and a different
    `baseVersion` moves the copy onto the live row only when it equals the live version (the CMS's "reopen
    on the current version" after an apply conflict, `12` §4). Anything else is `409 VERSION_CONFLICT` with
    `currentVersion` (the live version, or the copy's version when the copy moved);
  - `payload` is the kind's update body without `version`, plus its children as whole ordered lists:
    `stops`, `blocks`, `occurrences`, `placements`, `positions`, `categoryIds`, `media` (non-INLINE
    bindings), each item carrying the existing row's `id` when it has one. Triggers stay rows of their own
    (D-026) and are not in the payload;
  - validation is a dry run: the kind's own write runs on the live rows in a transaction that is always
    rolled back, so a payload is checked exactly like a save of a DRAFT and the response carries `resource`,
    the kind's read DTO as it would read after apply. Errors carry payload paths (`stops[2].poiId`);
  - apply locks the live row (`FOR UPDATE`), compares its version with `baseVersion`, runs the same write,
    and deletes the copy in one transaction; each child collection is replaced by diffing on `id`
    (missing deleted, unknown created, the rest updated, then ordered);
  - `GET .../working-copy` returns the copy with `resource` rendered by the same dry run, or `resource` null
    with `errors` when the payload no longer passes the rules, so the author can still discard it
    (QUESTIONS.md Q-016);
  - the last editor is `updatedBySubject` of the copy until Audit Events exist; no display name is stored
    (AGENTS.md §4 rule 4);
  - a direct `PUT` of an ACTIVE Resource and every child endpoint under an ACTIVE parent keep answering
    `422 RESOURCE_NOT_EDITABLE`, naming the working copy, instead of writing into the copy as `07` §14 says
    for child writes: the CMS sends the whole state in one `PUT`, and patching a JSON payload per child
    endpoint is deferred (GAPS.md G-034);
  - each kind's handler lives in its `api` package beside the response DTOs it renders; the kinds' update
    use cases expose their write without the status check for it.
  - Supersedes the first bullet of D-019 and the "only DRAFT until working copies exist" parts of D-021 to
    D-025.
- Why: Milestone 8 (`08`) and AGENTS.md §4 rule 13; owner priority over the rest of Milestone 8.
- Alternatives: a patch per child endpoint into the payload (deferred); validation rules duplicated per kind
  for the save (rejected: two rule sets drift); storing the editor's display name (rejected, rule 4).
- Affected specs: `03` §1; `06` §17; `07` §14, §15; `08` Milestone 8; `11` §4; `12` §4, §5.

## D-028 (2026-10-22) — Triggers open Places, Routes, Guides, and Events

- Type: spec-interpretation (owner request 2026-10-22)
- Decision: `InteractionTrigger.targetResourceType` admits `POI`, `PLACE`, `ROUTE`, `GUIDE`, and `EVENT`
  (changeset 0034 widens the check of 0032; `TriggerTargetType`). Interpretations:
  - the target is a Resource of that kind in the Project that is not archived, looked up by its kind
    (`RESOURCE_NOT_FOUND`, `ARCHIVED_REFERENCE`, as for POIs in D-026);
  - a non-archived trigger keeps its target in use for every kind (`03` §1); Guides gain a reference source
    (`GuideReferenceSource`), so their deactivation and archival are now refused with `RESOURCE_IN_USE` while a
    trigger opens them, like the other kinds;
  - a Route Stop gets no trigger of its own: the triggers of its Point of Interest open it (`03` §14);
  - Pages wait for Milestone 6; every other rule of D-026 (types, codes, surfaces, edited in place, live rows
    outside the working copy of D-027) holds for every kind;
  - the rollback of 0034 restores the POI-only check and fails while triggers of other kinds exist; they must
    be archived and deleted first.
- Why: `12` §3 puts the trigger card among the parts "common to every kind"; `03` §14 does not restrict the kind.
- Alternatives: one trigger table per kind (rejected: one generic target, like ResourceAsset).
- Affected specs: `03` §1, §14; `07` §14; `11` §9; `12` §3.

## D-029 (2026-10-22) — Project surfaces, the public web address, and QR images

- Type: spec-interpretation and owner decisions (2026-10-22: a minimal Surfaces settings; https, or http for
  localhost)
- Decision: `project_surfaces` (changeset 0035, group 2) and `GET`, `PUT /api/v1/projects/{p}/surfaces` (`03` §2,
  `07` §14, `12` §4 Change surfaces); `qr.png` renders real images (`07` §14, `11` §9). Interpretations:
  - the body is `{surfaces: [{surface, enabled, configuration}]}`; `GET` always lists WEB, MOBILE, and KIOSK in
    that order, a surface without a row reading as disabled; `PUT` writes the listed surfaces and leaves the
    others as they are;
  - `configuration` keys are known per surface and unknown ones refused (`04` §6): WEB takes `publicBaseUrl`,
    MOBILE and KIOSK nothing yet; the Template's `supportedSurfaces` are not checked, as no Template exists
    (G-035);
  - `publicBaseUrl` is an origin (`03` §2): `https`, or `http` for `localhost`, `127.0.0.1`, and `[::1]` (owner);
    no path, query, fragment, or user; scheme and host lowercased, no trailing slash; blank removes it;
  - an address counts only while WEB is enabled;
  - a QR code encodes `{publicBaseUrl}/{defaultLanguage}/{pois|places|routes|guides|events}/{slug}?t={code}`; the
    default language because `11` §9 redirects other codes to it and a printed code should open in the Project's
    main language; the code is URL-encoded;
  - the image is a 512-pixel black-on-white PNG, error correction M, quiet zone 2, encoded with ZXing core 3.5.3
    (Apache-2.0) and written with `javax.imageio`; trigger responses carry `runtimeUrl`, the encoded address,
    which the CMS shows (`12` §4 Generate a QR code);
  - the enum `Surface` moved from `trigger.domain` to the new `surface.domain`.
- Why: `qr.png` needed the address (G-033); the owner wants real QR images now, ahead of the rest of Milestone 2.
- Alternatives: a backend property as a temporary address (rejected: one address for every Project); the CMS
  drawing the real image (rejected: `07` §14 makes `qr.png` the source).
- Affected specs: `03` §2, §14; `04` §6; `07` §14; `08` Milestones 2, 3; `11` §9; `12` §3, §4.

## D-030 (2026-10-22) — List parameters of the content collections

- Type: spec-interpretation (owner request 2026-10-22, next item of the to-do list)
- Decision: the lists of Places, Points of Interest, Routes, Guides, Events, and Categories take, besides
  `status`, `q`, `pending`, and paging (`07` §14, `06` §16):
  - `language`: `q` matches the titles (Category names) of that language only; a code that is not an enabled
    language of the Project is `422 UNKNOWN_LANGUAGE`; a POI's exact exhibit code and a Category's code still
    match in every language, as they have none;
  - `featured=true|false` on the five kinds that have the flag (not Categories);
  - `categoryId` on POIs and Routes: the Resources in that Category or in one of its subcategories (the two-level
    tree of D-024, as the CMS filter does), or `none` for those without a Category; an id of no Category of the
    Project matches nothing; anything else is `422 INVALID_FORMAT`;
  - `sort=<field>` or `<field>,asc|desc` over the indexed columns: `sortOrder` and `slug`, Categories `sortOrder`
    and `code`; anything else is `422 INVALID_SORT`; the default stays `sortOrder asc` and `createdAt` breaks
    ties;
  - Category search on the code became case-insensitive, like the names.
- Why: `07` §14 names `language` and `categoryId`, `06` §16 a `sort` over indexed columns; `featured` is the
  flag every Resource list shows. The shared parts live in `ListParams`, `ListFilters`, and `CategoryFilter`.
- Consequences: the CMS keeps filtering in the browser: it loads each whole list, which manual reordering needs
  (frontend D-017).
- Alternatives: sorting by `updatedAt` or a title (rejected: not indexed, `06` §16).
- Affected specs: `06` §16; `07` §14; `12` §3 Content library.

## D-031 (2026-10-23) — Per-language translation endpoints

- Type: spec-interpretation (owner request 2026-10-23, next item of the to-do list)
- Decision: `PUT` and `DELETE /api/v1/projects/{p}/{kind}/{id}/translations/{lang}` (`07` §14) for `places`,
  `pois`, `routes`, `guides`, `events`, and `categories`; maps and floor plans have no texts (`404`). Interpretations:
  - one generic implementation (`translation` package) over the working-copy handlers of D-027: the Resource's
    current state is read as a payload from its read DTO (`WorkingCopyHandlers.liveState`, INLINE media left
    out), the language is replaced, and the kind's own write validates and stores it, so every rule of a normal
    save applies and children, Categories, and media are kept as they are;
  - the `PUT` body is the kind's translation fields of that language plus `version` (the live row's version) and,
    once a working copy exists, `workingCopyVersion`; the language must be an enabled one (`422
    UNKNOWN_LANGUAGE` at `language`);
  - a DRAFT Resource is written in place under its `version` (`409 VERSION_CONFLICT` otherwise); an ACTIVE one
    through its working copy (`12` §3: "a translation saved on an ACTIVE Resource goes to its Working Copy"),
    started from the live state when there is none, under the copy's version rules of D-027;
  - the response is the Resource's read DTO either way (`07` §14); after a write into a copy the header
    `X-Working-Copy-Version` carries the copy's version for the next call, and CORS exposes it;
  - `DELETE` empties every text of that language, which then reads as missing; rows are kept, as saves never
    delete languages; the Project's default language cannot be emptied (`422 DEFAULT_LANGUAGE`); `version` and
    `workingCopyVersion` are query parameters;
  - child texts (`.../stops/{stopId}/translations/{lang}`, `.../blocks/{blockId}/translations/{lang}`) and Asset
    texts (`.../assets/{id}/translations/{lang}`) are separate later items.
- Why: G-026; the Translation workspace of `12` §3 saves one language at a time.
- Alternatives: one implementation per kind (rejected: six copies of the rules the handlers already hold).
- Affected specs: `07` §14; `12` §3 Translation workspace, §7.

## D-032 (2026-10-23) — Translation endpoints of Route Stops, Guide Blocks, and Assets

- Type: spec-interpretation (owner request 2026-10-23, next item of the to-do list)
- Decision: `PUT` and `DELETE` on `/routes/{id}/stops/{stopId}/translations/{lang}`,
  `/guides/{id}/blocks/{blockId}/translations/{lang}` (`07` §14 child paths), and `/assets/{id}/translations/{lang}`
  (`assets` is a `{kind}` like any other, `07` §14). Interpretations:
  - Stops and Blocks follow D-031: the child is found by its id in the parent's state (the working copy for an
    ACTIVE parent), its language replaced or emptied, and the parent's own write validates and stores it, rich text
    and INLINE images included; `version` and `X-Working-Copy-Version` are the parent's; the response is the
    parent's read DTO; a child that exists only in the copy has no id yet and cannot be addressed (`404`);
  - Assets are edited in place while not archived (D-025): the path writes one language and keeps the name, the
    decorative flag, and the other languages; the activation rule on alternative text still applies to an ACTIVE
    image; the body is the texts plus `version`, the response the Asset's read DTO;
  - every path checks the language (`422 UNKNOWN_LANGUAGE`) and refuses to empty the default one
    (`422 DEFAULT_LANGUAGE`).
- Why: G-026; the Translation workspace of `12` §3 translates stops, blocks, and media texts as well.
- Affected specs: `07` §14; `12` §3 Translation workspace, Media library.

## D-033 (2026-10-23) — Readiness

- Type: spec-interpretation (owner request 2026-10-23, next item of the to-do list)
- Decision: `GET /api/v1/projects/{p}/readiness` (`07` §14) returns `{activatable, blocking, warnings}` with items
  `{resourceType, resourceId, title, languageCode, path, code}`, computed on request in one read-only transaction
  from the live rows. One `ReadinessCheck` per kind lives in that kind's package; only `ACTIVE` Resources are
  evaluated. Interpretations:
  - `path` is the field path the CMS editors use (`translations.el.title`, `blocks[0].translations.en.text`,
    `stops[1].poiId`, `placements[2]`, `surfaces`, `workingCopy`), so the form can point at the field;
    `resourceType` is `PLACE`, `POI`, `ROUTE`, `GUIDE`, `EVENT`, `CATEGORY`, `MAP`, `FLOOR_PLAN`, `ASSET`, or
    `PROJECT`; `title` is the default-language title or name, or an Asset's, map's, or plan's internal name;
  - blocking `MISSING_DEFAULT_LANGUAGE_VALUE`: the title (Categories: name) in the default language; a Guide
    without blocks (`blocks`) or a TEXT block without text;
  - blocking `MISSING_ALTERNATIVE_TEXT`: an ACTIVE non-decorative image without default-language alternative text;
  - blocking `NO_SURFACE_ENABLED`; warning `PUBLIC_BASE_URL_MISSING` while WEB is enabled without an address;
  - blocking `DANGLING_REFERENCE`: an ACTIVE Resource pointing at a missing or archived one (a Route stop's POI, a
    Guide reference block's target, an Event's Place, POI, or Route, a map placement, a floor-plan position);
    a DRAFT target is not dangling, it is reported by the Route rule below where it matters;
  - Routes (G-030): blocking `ROUTE_WITHOUT_ACTIVE_STOP` and warning `ROUTE_FEW_STOPS`, outside the closed lists,
    proposed in QUESTIONS.md Q-017;
  - warning `MISSING_TRANSLATION`: a text the CMS counts that is filled in the default language and empty in
    another enabled language, per field; the Guide story is its first TEXT block;
  - warning `MISSING_ACCESSIBILITY_INFORMATION` for ACTIVE Places and POIs in every enabled language (`14` §2);
    accessibility information then gets no `MISSING_TRANSLATION` of its own, so a field is not reported twice;
  - warning `MISSING_TRANSCRIPT`: an audio or video Asset bound to ACTIVE content without a default transcript;
  - warning `PENDING_WORKING_COPY` for every working copy;
  - not produced yet (G-036): `TEMPLATE_VALIDATION_FAILED`, `THEME_CONTRAST_FAILED`, `MISSING_CAPTION_TRACK`,
    `MAP_WITHOUT_POI_LIST`, `CAPABILITY_DISABLED`, `UNREACHABLE_PAGE`, `AI_OUTPUT_UNREVIEWED`;
  - items are ordered Project first, then by kind, title, and path, so the answer does not depend on check order.
- Why: Milestone 8; `12` §5 makes readiness the single source of "what is missing" for the Dashboard, activation,
  and the form.
- Alternatives: stored readiness (rejected: `07` §14 computes on request); per-row queries (rejected: one bulk
  query per kind).
- Affected specs: `03` §1, §2; `07` §14; `08` Milestone 8; `12` §3, §5; `14` §2.

## D-034 (2026-10-26) — RFC 7807 problem details and the platform correlation header

- Type: spec-interpretation (owner request 2026-10-26, next item of the to-do list; part of M0a)
- Decision: every error is an RFC 7807 problem details body served as `application/problem+json` (`07` §15,
  [ARC_7.9]); every response carries `X-UNICHE-Request-Id` (`10` §8). Interpretations:
  - members: `type` = `urn:uniche:iguide:problem:<code>`, `title` (the code in words, the same for every
    occurrence, as RFC 7807 asks), `status`, `detail` (this occurrence), `code`, `requestId`, and when present
    `errors[{path, code, message}]`, `currentVersion` (`VERSION_CONFLICT`), `references` (`RESOURCE_IN_USE`);
    the codes are the ones already in use;
  - on `VERSION_CONFLICT`, `modifiedBySubjectDisplayName` and `modifiedAt` are present and null until Audit
    Events exist (G-023); `conflictingFields` is omitted, as `07` §15 allows where it is not computed, and the
    CMS compares the values itself;
  - framework failures share the body: unreadable body `400 MALFORMED_REQUEST`, unknown path
    `404 RESOURCE_NOT_FOUND`, `405 METHOD_NOT_ALLOWED`, `415 UNSUPPORTED_MEDIA_TYPE`, `500 INTERNAL_ERROR`, and
    Spring Security's `401 UNAUTHENTICATED` (keeping `WWW-Authenticate: Bearer`) and `403 FORBIDDEN`;
  - `X-UNICHE-Request-Id` is echoed when the client sends a safe value, else generated; `X-Request-Id` is
    accepted inbound as an alias and no longer sent back; CORS allows both inbound and exposes
    `X-UNICHE-Request-Id`; Catalogue calls carry `X-UNICHE-Request-Id` and `X-Correlation-Id` (D-012);
  - `qr.png` also declares `application/problem+json`, so its errors can be written to an image client.
- Why: G-001, G-002; the CMS matches on `code` and reads `errors` by path.
- Alternatives: Spring's `ProblemDetail` type (rejected: the existing envelope keeps the extension members and
  the no-rejected-value rule in one place).
- Affected specs: `07` §5, §15; `10` §8; `08` Milestone 0.


## D-035 (2026-10-26) — MuseMind translation through a backend port, with a labelled mock

- Type: spec-interpretation (owner request 2026-10-26, to-do item "MuseMind AI translation, AI markers, and 'mark
  reviewed'"; owner choice: all of it, against a mock gateway; first slice of Milestone 13)
- Decision: the backend calls the UnicheAIGateway, never the CMS (AGENTS.md §4 rule 3; answers the first point of
  `09` §16 for now, QUESTIONS.md Q-018). Interpretations:
  - port `TranslationGateway` (package `ai`), adapter chosen by `iguide.ai.gateway.mode` (`IGUIDE_AI_GATEWAY_MODE`):
    `disabled` (default; every call `503 AI_GATEWAY_UNAVAILABLE`) or `mock` (`musemind-mock`: the text prefixed with
    `[MuseMind mock · <src>→<tgt>] `, a `mock-<uuid>` request id, no citations). The startup check refuses `mock`
    under the `prod` profile and any other value; `compose.yaml` and the test profile use `mock`. The real adapter
    reports `AVAILABLE` and waits for the gateway API (G-037). `GET /api/v1/ai/status` → `{translation}`;
  - `POST .../{kind}/{id}/translations/{lang}:translate`, body `{version, workingCopyVersion?}`: the non-blank
    default-language texts of the Resource as the workspace shows them (its working copy's when it has one) are
    translated and written like `PUT .../translations/{lang}` (D-031: same response and `X-Working-Copy-Version`);
    texts the default language lacks keep the target's own. `422 DEFAULT_LANGUAGE` for the default language,
    `422 NOTHING_TO_TRANSLATE` (path `translations.<default>`) when it has no text;
  - provenance in the `03` §1 shape, `function: "translate"`, `generatedAt` and `reviewedAt` as ISO instants; a
    DRAFT's on the live row, an ACTIVE Resource's in the working copy's new `translation_provenance` column
    (changeset 0036, language → provenance, `null` clearing it), written to the rows on apply and dropped on discard.
    It is written in SQL so the copy's `version` does not move;
  - `POST .../translations/{lang}:mark-reviewed` (no body, no version: no text changes) sets `reviewedBySubject` and
    `reviewedAt` on the provenance the workspace shows (the copy's when it carries the language, else the row's);
    `404` when that is not `AI_GENERATED`; an already reviewed one stays as it is. Response `{resourceType,
    resourceId, languageCode, provenance, pending}`;
  - a person's `PUT .../translations/{lang}` that changes the language's text (trimmed, blank as absent) reviews an
    unreviewed machine translation (`03` §1); for an ACTIVE Resource the reviewed provenance goes into the copy and
    reaches the row on apply. `DELETE .../translations/{lang}` drops the language's provenance. Editor saves of the
    whole Resource and of working copies leave provenance as it is: review stays explicit there;
  - `GET /api/v1/projects/{p}/ai-provenance`: every `AI_GENERATED` entry, live rows and copy entries (`pending:
    true`); a language can have one of each;
  - readiness warning `AI_OUTPUT_UNREVIEWED` at `translations.<lang>` for ACTIVE Resources whose live row in an
    enabled language is unreviewed.
- Scope: the Resource's own texts of the six kinds; Route Stops, Guide Blocks (the story's rich text), and Assets
  carry no provenance yet; no `CONTENT_REVIEWED` Audit Event (Milestone 7); provenance reaches no visitor (G-037).
- Why: the to-do item and `12` §3 need the action now; the gateway API does not exist (`09` §16, `08` Milestone 13).
- Alternatives: the CMS calling the gateway with the user's token (rejected: AGENTS.md rule 3, and provenance must
  be written where the text is); a feature flag instead of a mode (rejected: the mode names the adapter and lets the
  CMS tell the mock apart).
- Affected specs: `03` §1; `07` §14; `09` §16; `12` §3; `08` Milestone 13.

## D-036 (2026-10-26) — MuseMind for Route Stops, Guide Blocks (the story's rich text), and Assets

- Type: spec-interpretation (owner request 2026-10-26, to-do item "AI translation of Route stops, Guide blocks,
  Assets, and the Guide story"; follow-up of D-035, same mock gateway)
- Decision: `:translate` and `:mark-reviewed` reach every translation row the Translation workspace edits, on the
  paths of their translation endpoints (D-032). Interpretations:
  - `POST .../routes/{r}/stops/{s}/translations/{lang}:translate` and `.../guides/{g}/blocks/{b}/translations/{lang}:
    translate`, body `{version, workingCopyVersion?}` of the Route or Guide, response its read DTO with
    `X-Working-Copy-Version` after a write into its copy (as D-031/D-032); the source is the child as the workspace
    shows it (the copy's when there is one); an unknown child is `404`; `422 NOTHING_TO_TRANSLATE` at
    `stops[i].translations.<default>` / `blocks[i]...`;
  - `POST .../assets/{a}/translations/{lang}:translate`, body `{version}` of the Asset, response the Asset; in place,
    since Assets have no working copy (G-034);
  - `:mark-reviewed` on the same paths, no body; response `{resourceType, resourceId, parentType, parentId,
    languageCode, provenance, pending}` with `resourceType` `ROUTE_STOP` / `GUIDE_BLOCK` / `ASSET` and the Route or
    Guide as parent (`null` for an Asset, and for a Resource's own texts); `GET .../ai-provenance` entries gain the
    same two fields;
  - rich text: every node with text is sent as its own gateway field (`text.<i>`), and the translation keeps the
    source's nodes (type, level) with the translated texts; `asset` (image) nodes are kept as they are. A Guide
    Block's caption is a plain field;
  - provenance lives on the child's or Asset's translation row; for an ACTIVE Route or Guide the copy's
    `translation_provenance` carries it under `stops/<stopId>/<lang>` or `blocks/<blockId>/<lang>` (own texts keep
    the language as key; no changeset) and apply writes it to the row of that id. A stop or block the apply
    recreates (its Point of Interest changed) has a new id and loses it (G-037);
  - review as D-035: a person's `PUT` of a stop's, block's (rich text compared node by node, blank nodes ignored),
    or Asset's language that changes its text reviews an unreviewed machine translation; `DELETE` drops the
    provenance; editor saves of the whole Resource, its working copy, or the whole Asset leave it alone;
  - readiness `AI_OUTPUT_UNREVIEWED`: on the ACTIVE Route or Guide at `stops[i].translations.<lang>` /
    `blocks[i].translations.<lang>` (live order), on an ACTIVE Asset at `translations.<lang>`.
- Why: the to-do item; `03` §1 asks provenance of every machine-written text, `12` §3 the action on every cell of the
  workspace.
- Alternatives: one call translating a whole Route or Guide with its children (rejected: one row, one provenance,
  one request id; the CMS chains the calls); flattening rich text into one string (rejected: loses the node structure
  and image positions).
- Affected specs: `03` §1, §11; `07` §14; `09` §16; `12` §3; `08` Milestone 13.

## D-037 (2026-10-27) — Apply refuses changes that would add blocking readiness issues

- Type: spec-interpretation (owner decision 2026-10-27, to-do A39; spec question Q-019)
- Decision: `POST .../{kind}/{id}/working-copy:apply` validates the payload like a normal save (`03` §1) and, in the
  same transaction, compares the Project's blocking readiness issues before and after the write (`14` §2: "Blocking
  issues prevent ... application of a working copy"). When the write adds a blocking issue the Project did not have,
  the apply answers `422 VALIDATION_ERROR` and rolls back; the live rows and the copy stay as they were.
  Interpretations:
  - only new issues count, so an apply may fix issues, and issues elsewhere (such as `NO_SURFACE_ENABLED`) never trap
    an author;
  - issues are compared as multisets keyed by Resource, language, code, and path with list indexes dropped: moving a
    stop does not make its existing finding new, a second finding of the same kind is;
  - each new issue is one error item with the readiness code (`ROUTE_WITHOUT_ACTIVE_STOP`, `MISSING_DEFAULT_LANGUAGE_VALUE`,
    `DANGLING_REFERENCE`, `MISSING_ALTERNATIVE_TEXT`, ...) at the issue's path when it concerns the applied Resource
    (the CMS form paths), else at an empty path with a message naming the other Resource;
  - the evaluation is `GetReadinessQuery.evaluate`, the same one `GET .../readiness` uses, run inside the apply's
    transaction after a flush (`workingcopy/application/ApplyReadinessGate`).
- Why: an ACTIVE Route could be applied with no ACTIVE stop, and an ACTIVE Resource without its default-language
  title, although activation refuses both.
- Alternatives: re-running each kind's activation rules on apply (rejected: duplicates readiness and misses
  references); refusing on any blocking issue (rejected: an unrelated Project-level issue would block every apply).
- Affected specs: `03` §1; `07` §14; `14` §2.


## D-038 (2026-10-30) — Guide readiness covers every block; an image alone fills a text block

- Type: spec-alignment (to-do A42, found by the CMS on 2026-10-27, frontend D-031; `03` §11: the story is ordered
  blocks, text or reference; `03` §1 readiness)
- Decision:
  - `GuideReadiness` warns `MISSING_TRANSLATION` at `blocks[i].translations.<lang>.text` for every TEXT block, not
    only the first, which had stood for the single story the CMS once edited (D-022);
  - a REFERENCE block's caption is optional; once the default language has one, every other enabled language
    without it warns at `blocks[i].translations.<lang>.caption`;
  - a TEXT block counts as written in a language when it holds non-blank text or an `asset` node
    (`RichText.hasContent`), so a block that is only an image no longer refuses activation (`422` "Text or an
    image is required") nor blocks readiness.
- Why: the story is several blocks since 2026-10-01; translators saw nothing missing past the first block, and an
  image between two paragraphs could not stand alone.
- Alternatives: requiring a caption on every reference (rejected: `03` §11 makes it optional).
- Evidence: TRACEABILITY.md R24.
- Affected specs: `03` §1, §11.

## D-039 (2026-11-03) — Organizations passthrough and the Catalogue's Project timestamps

- Type: implementation (owner-instructed 2026-11-03, to-do A47, ahead of the rest of M1c; `02` §4, §9, §11; `07` §2,
  §5, §14)
- Decision:
  - `GET /api/v1/organizations` relays the Catalogue's `GET /api/v1/organisations` with the caller's token through
    `CatalogueClient.listOrganizations` (client-wide status mapping, an empty body is an empty list) and
    `project/application/ListOrganizationsPassthroughQuery`. It reads and writes no local state; the answer is our
    own `OrganizationResponse` `{id, name, slug, status}` in the Catalogue's order and filtering (the
    organizations the caller manages or belongs to, all of them for a platform admin). The class sits in `project`
    next to the organization Project listings, not in `platform.organization`, which holds the snapshots slated for
    removal (G-006);
  - `CatalogueProjectDto` reads `createdAt` and `updatedAt` (the Catalogue's `date-time`, live OpenAPI 2026-11-03)
    as strings and parses them leniently (`CatalogueTimestamps`: ISO instant or offset; without an offset read as
    UTC); a missing or unparseable value is `null` and never fails a read;
  - the companion upsert writes them to `catalogue_created_at` / `catalogue_updated_at` (columns since changeset
    group 01); a value the Catalogue left out keeps the stored one. `AccessibleProject`, `ProjectView` (every
    factory), and `ProjectResponse` carry them as `createdAt` / `updatedAt` (`Instant`, ISO-8601 UTC in JSON,
    `null` when unknown): from the companion row on reads and writes, from the Catalogue body on listings;
  - the dates are the Catalogue's times for the Project, never those of the companion row.
- Why: the CMS Project picker and Dashboard showed "Organisation 5d1e7a52" and "—" for Created / Last updated.
- Alternatives: the CMS calling the Catalogue directly (rejected: `02` §11 routes it through this backend);
  exposing `CatalogueOrganizationDto` as `/me/authorization` does (rejected: `07` §2 asks for our own response
  DTO); refusing a Project with a bad timestamp (rejected: display data must not cause an outage).
- Evidence: TRACEABILITY.md R25. Closes GAPS.md G-016 and G-021.
- Affected specs: none.

## D-040 (2026-11-04) — Templates, style presets, and Project initialization (M2a and M2i)

- Type: implementation and spec-interpretation (owner-instructed 2026-11-04: the CMS "Create a Project" steps 2 and
  3 and "resume initialization"; `04` §1–12, §15; `08` Milestone 2; `07` §8, §14, §15; `03` §2–3; `09` §19, §20;
  `12` §3, §4)
- Decision:
  - seeding: `src/main/resources/templates/{key}.v{version}.json` (`museum-multi-surface`, `outdoor-trail`,
    `multi-region-network`, all v1 and `ACTIVE`, the names `04` §15 gives; the mockup's "Outdoor trail v2" is not
    followed) and `src/main/resources/style-presets/{key}.json` (the nine presets of the prototype; the mockup's
    "Caldera Geopark" spelling) are read strictly (unknown member or value, missing member: startup fails) and
    validated together (`TemplateFileValidator`: surfaces, capabilities, known default languages, initial Category
    codes and icons, presets exist and use only `themeValues`, every preset and `defaultTheme` passes the contrast
    rules, `shellSchema` lists exactly the five shell keys and `defaultShell` sets each, `runtimeDefaults` only for
    supported surfaces and only keys the surfaces endpoint accepts, `initialPages`/`initialNavigations` empty until
    M6). `TemplateSeeder` runs as a `SmartInitializingSingleton`, after Liquibase and before the web server accepts
    requests, in one transaction;
  - checksum: SHA-256 of the canonical JSON (keys sorted, no whitespace) of the Template's `definition`, and of a
    preset's `values`, so line endings and indentation of a checkout do not count. A changed checksum on a version
    with a `project_template_applications` row, or on a preset a `project_themes` row uses, fails startup; on an
    unused one the row is rewritten. `status`, `name`, and `description` are outside the checksum, so a version is
    deprecated or retitled in the file (`04` §15) (QUESTIONS.md Q-021). A Template's id is a name-based UUID of
    `iguide:template:{key}:v{version}` (`TemplateSeeder.templateId`), equal in every environment. Rows whose file is
    removed stay;
  - schema (changesets 0037–0041 in group 2, 0042 in group 3 for the logo foreign keys, because `assets` is created
    after group 2): `application_templates` (`template_key`, since `key` is the spec name, not a good column),
    `style_presets` (`preset_values`), `project_template_applications` (unique `project_id`, snapshot of key,
    version, names, checksum, and definition), `project_settings`, `project_themes` (typed columns, `#RRGGBB` and
    enum checks, composite `(project_id, asset_id)` foreign keys to `assets`). No `previewAssetId` (no platform
    Asset store; preview metadata waits), no preset `status`. No Row-Level Security: no table has a policy yet
    (G-009);
  - `GET /api/v1/templates` lists the `ACTIVE` versions (key ascending, version descending) as
    `{templates: [...]}`; `GET /api/v1/templates/{templateId}` returns any status or `404 RESOURCE_NOT_FOUND`.
    `name`, `description`, preset and initial Category names follow `Accept-Language` (exact tag, then primary
    language, then `en`); the response carries the offered presets with every value a tile needs, a `suggested`
    flag, and `fonts` (`{key, family}`): font keys are the `ThemeFont` enum of the prototype's families (`09` §19
    open, Q-022);
  - `POST /api/v1/projects/{projectId}/initialize` takes `{templateId, stylePreset?, languages?, defaultLanguage?,
    surfaces?, featureConfiguration?}`; a language is a code or `{languageCode, displayName?, direction?}`. Defaults:
    the Template's `defaultStylePreset`, its `defaultLanguages` (names and direction from `KnownLanguages`, else the
    Project's existing row), the first language as default, every supported surface, every declared capability
    enabled (`CHATBOT` disabled). One transaction after the Catalogue check: lock the companion row
    (`SELECT ... FOR UPDATE`, so a concurrent second call waits and then gets `409`), settings, theme from the
    preset with `shell` from `defaultShell`, languages through the languages endpoint's own rules
    (`ProjectLanguagesWriter`, including `DEFAULT_LANGUAGE_INCOMPLETE`), surfaces (listed enabled, others disabled,
    saved configuration such as `publicBaseUrl` kept over `runtimeDefaults`), Categories (DRAFT, translations in the
    enabled languages, a code the Project already has skipped), the provenance row, `initializedAt`. A failure
    rolls everything back, so a re-run never duplicates; rows the author set in App settings before are updated.
    The answer is the Project as `GET /projects/{id}` gives it. `409 PROJECT_ALREADY_INITIALIZED` once
    `initializedAt` is set; `422 VALIDATION_ERROR` with paths `templateId` (`REQUIRED`, `TEMPLATE_NOT_FOUND`,
    `TEMPLATE_NOT_ACTIVE`), `stylePreset` (`STYLE_PRESET_NOT_OFFERED`), `languages`, `languages[i].*`,
    `defaultLanguage` (`DEFAULT_LANGUAGE_NOT_LISTED`), `surfaces`, `surfaces[i]` (`SURFACE_NOT_SUPPORTED`,
    `DUPLICATE_VALUE`, `REQUIRED`), `featureConfiguration.<CAPABILITY>` (`CAPABILITY_NOT_IN_TEMPLATE`,
    `UNKNOWN_CAPABILITY`);
  - before initialization nothing restricts languages and surfaces (the CMS sets them in App settings); after it,
    `PUT .../surfaces` refuses enabling a surface the Template does not support (`SURFACE_NOT_SUPPORTED` at
    `surfaces[i].surface`) through the `SurfaceSupport` port the template package implements;
  - `GET`, `PUT .../theme` and `GET`, `PUT .../settings` answer `409 PROJECT_NOT_INITIALIZED` before
    initialization. The theme `PUT` replaces typed values, preset key, logos, and shell, with `version`
    (`VERSION_CONFLICT`); a `null` typed value takes the Template default (`04` §10, `effective` in the answer);
    errors `INVALID_COLOR`, `VALUE_NOT_ALLOWED`, `STYLE_PRESET_NOT_OFFERED`, `UNKNOWN_KEY` / `VALUE_NOT_ALLOWED` /
    `TOO_LONG` at `shell.<key>`, `ASSET_NOT_FOUND` / `ASSET_NOT_IMAGE` at the logo fields, `CONTRAST_TOO_LOW` at
    `textColor` (4.5:1), `primaryColor`, `secondaryColor` (3:1) against the effective background. Choosing a preset
    is a `PUT` with its values; `stylePresetModified` compares the effective values with the preset's. The settings
    `PUT` sets the listed capabilities (others keep their value) and `timezone` (IANA, `INVALID_TIMEZONE`);
    `applicationStatus` is read-only and `DRAFT` (no transition endpoint yet). The default language stays in
    `project_languages.is_default` (D-019), not in `project_settings`;
  - readiness: `THEME_CONTRAST_FAILED` at `theme.<field>` and `TEMPLATE_VALIDATION_FAILED` at `surfaces` for an
    initialized Project; nothing for an uninitialized one;
  - Project picker (`12` §3): `GET` and `PATCH /projects/{id}` and the initialize answer add `initializedAt`,
    `template {id, key, version, name}`, `stylePresetKey`, `applicationStatus`, `enabledSurfaces`, read through the
    `ProjectInitializationReader` port. The organization listing stays a passthrough that touches no local state
    (`02` §11), so it carries none of them and `initialized` stays `false` there; the CMS reads `GET /projects/{id}`
    per Project (QUESTIONS.md Q-020). Before initialization `applicationStatus` is `DRAFT` and `enabledSurfaces`
    lists what App settings enabled; nothing of a failed attempt is kept, so "resume" opens step 2 with the
    Template's defaults;
  - packages: `template` (Templates, presets, seeding, provenance), `theme`, `settings`, `initialization` (the use
    case and the reader, so `template` depends on neither `theme` nor `settings`). Not built: Pages, Page
    Components, Navigation (M6), `TEMPLATE_APPLIED` (no audit table, M7), the activation endpoint (GAPS.md G-038).
- Why: the CMS "Create a Project" needs the Template and style step, the languages and surfaces step, and resume.
- Alternatives: raw-file checksums (rejected: a CRLF checkout would fail startup); checksum over the whole file
  (rejected: the spec moves status through the file); enriching the organization listing from local rows
  (rejected for now: `02` §11 forbids local state in listings, Q-020); materializing `runtimeDefaults` keys the
  surfaces endpoint refuses (rejected: the next CMS save would fail, G-035); `404` for theme and settings before
  initialization (rejected: the CMS needs to tell "not initialized" from "no access").
- Evidence: TRACEABILITY.md R26.
- Affected specs: `03` §2; `04` §3, §6–11, §15; `07` §8, §14, §15; `12` §3, §4. Questions Q-020, Q-021, Q-022.

## D-041 (2026-11-04) — Places bind media

- Type: owner decision (2026-11-04: "in places I should have the opportunity to add images"), revising D-025
- Decision: `PLACE` joins the `ResourceAsset` kinds. `.../places/{id}/resource-assets` and `:order` work as for the
  other kinds (DRAFT Place only, Asset of the same Project, usage and type rules, archival of a bound Asset refused
  with the Place named in `references`); `PlaceResponse` carries `media` in order, in reads, lists, working copies
  and applies; the Place working copy carries `media` as one list (INLINE excluded) and apply replaces the bindings,
  as for Points of Interest (D-027). Changeset `0043-resource-assets-for-places` widens
  `ck_resource_assets_resource_type`. The CMS offers a primary image and a gallery.
- Why: `03` §4 lists `ResourceAsset.resourceType` without restricting it; D-025 had left Places out only because
  the CMS mockup showed no media on them.
- Alternatives: a single `heroAssetId` column on `places` (a second mechanism next to the bindings).
- Affected specs: `03` §4, §5; `07` §14; `12` §3 Place.


## D-042 (2026-11-09) — `contentRevision` upkeep

- Type: spec implementation (owner request 2026-11-09, CMS to-do B3)
- Decision: `ContentRevision` (settings module) raises `project_settings.content_revision` by one SQL increment in
  the caller's transaction on: apply of a working copy; a lifecycle transition into or out of `ACTIVE` of every
  Resource kind and of triggers (DRAFT to ARCHIVED and ARCHIVED to DRAFT stay invisible, so they do not count); an
  in-place edit of an `ACTIVE` trigger or `ACTIVE` Asset (both are edited live, D-025, D-026); every save of the
  theme, languages, surfaces, and settings. Saves into a working copy, DRAFT edits, Project initialization, and the
  passage of time do not raise it. The entity maps the column `updatable = false`, so a settings save never writes
  back a stale value, and the increment does not move the settings' own `version`. A Project without settings has
  no revision and the increment touches no row. Navigation and `applicationStatus` join when they exist (M6, G-038).
- Why: `03` §2 (lines 310-314), `11` §4, `07` §14; the CMS shows the revision on the Dashboard (`12` §3).
- Alternatives: incrementing through the entity (lost steps under concurrent mutations and a settings
  `VERSION_CONFLICT` for authors who never touched the settings); counting every status change (the spec counts
  only what visitors could see).
- Affected specs: `03` §1, §2; `07` §14; `11` §4; `08` M8.

## D-043 (2026-11-10) — Audio and video durations; deleting stored objects nothing points at

- Type: spec implementation and interpretation (owner request 2026-11-10, CMS to-do B7)
- Decision:
  - `durationSeconds` (`03` §4) is read from the container header of every `AUDIO` and `VIDEO` upload and
    replacement by `MediaDuration`, in plain Java: ISO base media (MP4, M4A, MOV, 3GP) `mvhd`; Matroska and WebM
    `Info` `Duration` × `TimecodeScale`; MP3 Xing, Info, or VBRI frame count, else the constant bitrate; WAV `data`
    over the byte rate; Ogg Vorbis and Opus last granule over the sample rate (Opus less its pre-skip); FLAC
    `STREAMINFO`. Nothing is decoded or transcoded, which `09` §2 leaves open. A format it does not know, or a
    header it cannot read, leaves `durationSeconds` null as before; the upload never fails over it. Assets uploaded
    earlier keep null until their file is replaced;
  - stored objects no Asset points at are deleted (`04` §10: binary operations outside the database transaction
    need an outbox or cleanup strategy). `AssetStorage.delete` is added (`07` §10 lists it on the port):
    - an upload or replacement whose metadata write fails deletes the object it just stored, best effort (logged
      when the store refuses);
    - a replacement queues the old object in `asset_storage_cleanups` (changeset 0044, group 3) in the same
      transaction, due after the signed URL lifetime plus five minutes (`AssetRules.URL_VALIDITY`), so a page that
      is open keeps showing the old file until its URL expires;
    - `PurgeAssetObjectsUseCase` runs every `iguide.assets.cleanup-interval` (default `PT10M`, not in the `10` §3
      property contract yet), deletes the due objects, keeps an entry whose delete failed for the next run, and
      never deletes an object an Asset points at. It is the one reader across Projects; it reads only keys, which
      name their Project, and needs an exception when Row-Level Security arrives (G-022);
  - Assets are never deleted (archival is soft, `03` §1), and Project data cleanup stays open (`09` §9), so these
    are the only objects that lose their Asset today. The replacement now also re-checks that the Asset is still a
    DRAFT inside its transaction.
- Why: GAPS G-032 ("`durationSeconds` is never measured", "objects of replaced files and of failed metadata writes
  are never cleaned up"); the CMS library and editor already show the duration (`12` §3 Media).
- Alternatives: `ffprobe` in the container (a native binary and a process per upload, for a header read);
  a media library dependency (none covers MP4, WebM, MP3, Ogg, and FLAC together without decoding); deleting the
  old object at once (breaks signed URLs already handed out); listing the bucket for orphans (reads every object
  and races uploads in progress).
- Affected specs: `03` §4; `04` §10 (line 290); `07` §10; `09` §2, §9; `10` §3.

## D-044 (2026-11-10) — Placements and positions of an ACTIVE map or plan go into its working copy

- Type: spec implementation (owner request 2026-11-10, CMS to-do B8)
- Decision: `POST`, `PUT`, and `DELETE` on `.../maps/{id}/resources` and `.../floor-plans/{id}/positions` write into
  the parent's working copy when the parent is `ACTIVE`, as `07` §14 says for child writes ("never into the live
  child rows ... the response is the same shape either way"); a DRAFT parent is written directly as before and an
  ARCHIVED one is still `422 RESOURCE_NOT_EDITABLE`. Interpretations:
  - `PatchWorkingCopyUseCase` (workingcopy module) takes the copy's payload, or the parent's live state when it has
    no copy, applies one child change, and saves it through `SaveWorkingCopyUseCase`, so the dry run validates it
    and the copy's version rules hold: a copy saved by someone else between the read and the save is
    `409 VERSION_CONFLICT`. The endpoints carry no version, as on a DRAFT parent;
  - the checks and error paths are those of a DRAFT parent (`resourceType`, `resourceId`, `latitude`, `x`, ...;
    a second placement of a Resource is `409 DUPLICATE_VALUE` at `resourceId`; an unknown id `404`), checked against
    the copy's list;
  - a placement or position added this way gets an id in the copy, which later `PUT` and `DELETE` calls use; apply
    gives it a row of its own with a new id (the rows' ids are generated by the database layer). Existing ones keep
    their ids through apply;
  - `GET .../resources` and `.../positions` still list the live rows; the pending state is read from
    `GET .../working-copy`, like every other pending field;
  - the row writes the apply uses moved to `MapPlacementWriter` and `FloorPlanPositionWriter`, so the endpoint use
    cases can depend on the working copy without a dependency cycle through the handlers;
  - the map's and plan's own fields, `placeId` included, still change only through a whole working copy (`PUT` of an
    ACTIVE map stays `422`, D-027); route stops, guide blocks, and event occurrences under an ACTIVE parent stay
    `422` too (G-034).
  - Supersedes, for these two child collections, the D-027 bullet that kept them `422`.
- Why: `07` §14; GAPS G-034; CMS to-do B8.
- Alternatives: patching the stored JSON without the dry run (would accept a payload apply then refuses);
  `@Lazy` injection to break the cycle (hides it; no precedent in the code base).
- Affected specs: `07` §14; `03` §1, §7, §8; `12` §7.

## D-045 (2026-11-10) — Stops, blocks, and occurrences of an ACTIVE Route, Guide, or Event go into its working copy

- Type: spec implementation (owner request 2026-11-10, CMS to-do B16)
- Decision: the child endpoints of `07` §14 for Route stops (`POST`, `PUT`, `DELETE`, `PUT .../stops:order`), Guide
  blocks (the same four), and Event occurrences (`POST`, `PUT`, `DELETE`) write into the parent's working copy when
  the parent is `ACTIVE`, as D-044 does for map placements and floor plan positions; DRAFT parents are written
  directly as before and ARCHIVED ones are still `422 RESOURCE_NOT_EDITABLE`. Interpretations:
  - the same `PatchWorkingCopyUseCase`, now told which child it changed (`stops[2]`), so errors from the copy's dry
    run are reported at the child's own fields (`poiId`, `endsAt`, `resourceId`), as on a DRAFT parent
    (`ErrorPaths.outside`, the reverse of `within`); the D-044 endpoints report the same way;
  - the answer is the child as it stands in the copy, in the endpoint's usual shape: position or sort order from its
    place in the list, texts as sent (normalized when the copy is applied); a new child has an id in the copy and
    gets a row id at apply, as in D-044;
  - `PUT` on a stop or block upserts the languages it sends and keeps the others, as on a DRAFT parent; a block keeps
    its type; `:order` must list every child of the copy once (`422 INCOMPLETE_ORDER` at `ids`);
  - the occurrence limit counts the copy's occurrences;
  - the row writes the apply uses moved to `RouteStopWriter`, `GuideBlockWriter`, and `EventOccurrenceWriter`, as in
    D-044, so the endpoint use cases depend on the working copy without a cycle; the endpoint use cases answer
    `StopData`, `BlockData`, and `WantedOccurrence` records for both paths;
  - the child translation endpoints already wrote into the copy (D-032); every child endpoint of `07` §14 that
    exists now follows its rule. Pages and Navigation items join with M6.
  - Supersedes the D-027 bullet that kept child endpoints under an ACTIVE parent at `422`.
- Why: `07` §14; GAPS G-034; CMS to-do B16. The CMS editors save whole working copies and are not affected.
- Alternatives: as in D-044.
- Affected specs: `07` §14; `03` §1, §10, §11, §15.

## D-046 (2026-11-11) — Navigation: the PRIMARY menu and the FOOTER links

- Type: spec implementation and interpretation (owner request 2026-11-11, CMS to-do B15; part of Milestone 6)
- Decision: the `navigation` module, changesets 0045–0047 in the new group 5 file `05-pages-and-navigation.sql`
  (`navigations`, `navigation_items`, `navigation_item_translations`), and the paths of `07` §14 under
  `/navigations`. Interpretations:
  - `itemType` (named, not defined, in `03` §13) is `RESOURCE` (opens a Place, POI, Route, Guide, or Event: the
    kinds with a detail screen, `11` §9), `PAGE` (refused with `422 UNSUPPORTED_TYPE` until Pages exist), or `GROUP`
    (a heading of the PRIMARY tree with no target, whose children form a submenu); owner decision 2026-11-11.
    A GROUP needs a label in the default language, having no target title to fall back on; FOOTER items are flat
    links (`06` §12), so neither a parent nor a GROUP there;
  - a Navigation is created by `POST /navigations` `{context, name}`, at most one per context (`409 DUPLICATE_VALUE`
    at `context`); Templates define no initial Navigations (`initialNavigations` stays empty), so initialization
    creates none;
  - every read and write answers the whole Navigation with its items in tree order (each after its parent,
    siblings by `sortOrder`), each with its `depth` and its labels by language, so the editor redraws from one
    answer; `POST .../items` answers `201` with the Navigation;
  - items carry versions (`PUT`, `DELETE ?version=`, and the per-language label endpoints); a new item and a moved
    one go last among their new siblings and the gap they leave closes; deleting an item deletes its subtree (the
    database cascades); `:order` takes every item of the Navigation once in tree order and orders each sibling
    group, without changing parents; a parent under the item itself is `422 CYCLIC_PARENT` (`08` M6);
  - `labels` in an item body change only the languages they name, a blank label removes one; without a label the
    visitor app uses the target's title (`03` §13);
  - every write is live and raises `contentRevision` in its transaction (`03` §1, D-042); `NAVIGATION_UPDATED`
    (`05`) waits for the audit log (G-023);
  - menu items are referrers of their targets: they appear in "Used in" as `NAVIGATION` (title "Main menu: label")
    and, like every referrer, keep the target from being deactivated or archived, so a menu never offers a dead
    entry;
  - readiness: a GROUP without its default-language label blocks, its missing other labels warn
    (`MISSING_TRANSLATION`); a missing or archived target blocks (`DANGLING_REFERENCE`); a target whose capability
    is off warns (`CAPABILITY_DISABLED`, `03` §2), read through the new `CapabilityLookup`; paths are
    `items[i]…` in tree order;
  - the manifest's `navigation` (`11` §4) waits for the runtime API (Milestone 8, G-034).
- Why: `03` §13, `06` §11–12, `07` §14, `08` Milestone 6, `12` §3 Navigation editor.
- Alternatives: a nested JSON tree saved whole (concurrent edits of different items would conflict); deleting a
  group only when empty (the CMS asks first instead); archival allowed with a readiness warning (the menu would then
  point at nothing until someone noticed).
- Affected specs: `03` §1, §2, §13; `06` §11, §12, §15; `07` §14; `08` Milestone 6; `11` §4; `12` §3.
