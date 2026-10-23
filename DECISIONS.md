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

