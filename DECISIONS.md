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
