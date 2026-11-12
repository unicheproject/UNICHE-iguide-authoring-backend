# PLAN

Specs baseline: see AGENTS.md §3. Acceptance criteria come from `08`; every item also keeps
`./mvnw -B verify` green.

Goal (DECISIONS.md D-015): the CMS and this backend work end to end on a dev box against the live
Keycloak and Catalogue. Frontend counterpart: `../uniche_iguide_frontend/PLAN.md` C0a–C0c.

## Now

### M2 — Templates, style presets, and Project initialization (M2a seeding and the initialize slice)

- Owner-instructed 2026-11-04 (CMS "Create a Project" steps 2–3 and "resume initialization"), ahead of M1b;
  interpretations in DECISIONS.md D-040, spec questions Q-020 to Q-022.
- Status: backend code and tests green (TRACEABILITY.md R26): seeding with checksums, `GET /templates`,
  `POST .../initialize`, `GET`/`PUT .../theme` and `.../settings`. Open: Pages and Navigation (M6), `TEMPLATE_APPLIED`
  (M7), the activation endpoint, `contentRevision` (G-038). CMS counterpart: frontend C1, not yet synced.

### M13t — MuseMind translation, AI provenance, and review (mock gateway)

- Owner-instructed 2026-10-26 (to-do item "MuseMind AI translation, AI markers, and 'mark reviewed'"), ahead of
  the gateway API; interpretations in DECISIONS.md D-035, `09` §16 point in QUESTIONS.md Q-018.
- Status: backend code and tests green (TRACEABILITY.md R21) against the labelled mock; the CMS counterpart is
  frontend D-026. Since 2026-10-26 also Route Stops, Guide Blocks (rich text), and Assets (D-036, R22). Open: the
  real adapter, `CONTENT_REVIEWED` (G-037).

### M8w — Working copies for the content kinds

- Owner-instructed 2026-10-20, ahead of the rest of M8; interpretations in DECISIONS.md D-027, spec question Q-016.
- Status: backend code and tests green (TRACEABILITY.md R13); the CMS edits ACTIVE Resources through the copy
  (frontend D-016, unproven against the live realm).
  Open: `contentRevision`, Audit Events, `conflictingFields`, child endpoints into the copy (G-034).
  Since 2026-10-27 apply refuses a write that adds blocking readiness issues (D-037, Q-019, R23).
- Readiness: `GET .../readiness` since 2026-10-23 (D-033, R19); codes waiting for other milestones in G-036.
  Since 2026-10-30 a Guide warns per TEXT block and per caption, and an image-only text block counts (D-038, R24).

### M5t — Interaction Triggers on Points of Interest

- Owner-instructed after M3; interpretations in DECISIONS.md D-026; spec question QUESTIONS.md Q-015.
- Status: backend code and tests green (TRACEABILITY.md R12); the CMS POI editor has the "How visitors
  reach it" card. Since 2026-10-22 triggers also open Places, Routes, Guides, and Events (D-028, R14), with the
  card in their editors. Since 2026-10-22 `qr.png` renders real images from the WEB surface's address (D-029,
  R15). Open: capabilities, Pages (G-033); the rest of Surfaces (G-035).

### M3 — Assets, S3 storage, and media bindings

- Owner-instructed after M4c; interpretations in DECISIONS.md D-025; spec questions in QUESTIONS.md Q-014.
- Status: backend code and tests green (TRACEABILITY.md R11); MinIO in the local composition; the CMS
  adapters use the endpoints. Open: provenance warnings, runtime delivery (G-032); duration measurement and
  storage cleanup done 2026-11-10 (D-043).
- 2026-11-04: Places bind media too (owner request, D-041): primary image and gallery, working copy included.
- 2026-11-09: `contentRevision` upkeep (D-042): apply, visible status changes, live edits of ACTIVE triggers and Assets, theme, languages, surfaces, settings.
- 2026-11-10: audio and video durations from the container header; replaced and orphaned stored objects deleted (D-043).
- 2026-11-10: placements and positions of an ACTIVE map or floor plan go into its working copy (D-044).
- 2026-11-10: stops, blocks, and occurrences of an ACTIVE Route, Guide, or Event go into its working copy (D-045).
- 2026-11-11: Navigation (Milestone 6 part): PRIMARY tree and FOOTER links, items opening Resources or grouping a submenu, live writes with `contentRevision`, where-used, readiness (D-046). Pages are the rest of Milestone 6.

### M9e — Events and Event Occurrences

- Owner-instructed after M4g; interpretations in DECISIONS.md D-023.
- Status: backend code and tests green (TRACEABILITY.md R9). Open: the Project timezone and the `EVENTS`
  capability (G-024), event media (G-025), the CMS events calendar (C6).

### M4c — Categories and Resource categories

- Owner-instructed after M9e; interpretations in DECISIONS.md D-024; spec questions in QUESTIONS.md Q-013.
- Status: backend code and tests green (TRACEABILITY.md R10); the `categoryId` list filter exists since
  2026-10-22 (D-030, R16). Template `initialCategories` are materialized since 2026-11-04 (D-040, R26).

### M1a — Remove identity and organization snapshots and the `/me` cache

- Outcome: no local identity or organization table; `/me/authorization` is an uncached passthrough.
- Spec refs: `02` §5, §6, §10; `07` §3, §7; `10` §5; specs `CLAUDE.md` inv. 4.
- Closes: G-005, G-006, G-018. G-007 closed 2026-10-13 (`/me/authorization` passthrough, D-017).
- Depends on: nothing (parallel to M0a).
- Non-goals: RLS; changes to the Project companion row.
- Acceptance:
  - a new changeset drops `identity_snapshots` and `catalogue_organization_snapshots`; changesets
    0003 and 0004 stay unedited; Liquibase applies cleanly on an empty database and on one with the
    old tables;
  - no request writes any row other than the Project companion;
  - `GET /api/v1/me/authorization` calls the Catalogue on every request and returns its body;
  - no project endpoint succeeds without a valid Keycloak token.

## Next

### M0a — Platform error model, correlation, split CORS, OpenAPI contract

- Outcome: errors are RFC 7807 with `code`, URN `type`, `requestId`; `X-UNICHE-Request-Id` on every
  response; `/runtime/v1` open to every origin; contract committed and diffed.
- Spec refs: `07` §3, §15; `10` §3, §8; `08` Milestone 0; specs `CLAUDE.md` inv. 16, 18 [ARC_7.9].
- Closes: G-001, G-002 (inbound and response side), G-003, G-004.
- Status: problem details and the correlation header done 2026-10-26 (D-034, R20; G-001, G-002 closed); open:
  the `/runtime/v1` CORS split (G-003) and the committed OpenAPI contract (G-004).
- Depends on: nothing. Outbound header per DECISIONS.md D-012.
- Non-goals: Catalogue mock, RLS, `VERSION_CONFLICT` and `RESOURCE_IN_USE` envelopes (their milestones).
- Acceptance:
  - every error response is `application/problem+json`, carries `code` and `requestId`, the
    `X-UNICHE-Request-Id` header matches the body, and no body contains a rejected field value;
  - every response carries `X-UNICHE-Request-Id`, echoing a client value; `X-Request-Id` is accepted
    inbound as an alias; Catalogue calls carry `X-UNICHE-Request-Id` and `X-Correlation-Id` (D-012);
  - `401` keeps `WWW-Authenticate: Bearer` and the problem body;
  - a request to `/runtime/v1` from any origin receives `Access-Control-Allow-Origin: *` without
    credentials; a request to `/api/v1` from an unlisted origin does not;
  - `openapi/openapi.yaml` is committed; a test fails when the generated document differs from it.

### M1c — Tool scoping and the organizations passthrough

- Spec refs: `02` §2, §11, §12; `07` §6, §14; `08` Milestone 1; D-009, D-014. Closes G-008, G-016, G-017, G-021.
- Acceptance: a Catalogue `200` for another tool's Project returns `404` and writes no companion row;
  organization and deleted listings return only `iguide` Projects; `createdAt`/`updatedAt` reach the
  companion row; local slug validation matches the Catalogue; `GET /api/v1/organizations` passes through.
- Status: the organizations passthrough and the Project dates done 2026-11-03 (to-do A47, D-039, R25; G-016,
  G-021 closed); open: tool scoping (G-008), the slug rule (G-017).

### M0c — Local composition

- Spec refs: `10` §6; `08` Milestone 0; DECISIONS.md D-010. Closes G-012, part of G-011.
- Acceptance: `docker compose up` yields a healthy application against a containerized PostgreSQL;
  `make run` starts the app from Maven against the composed database; profiles for `cms`, `runtime`,
  `vibe-mock` are added when those repositories ship a Dockerfile.

### M1b — Row-Level Security and the two database roles

- Spec refs: `06` §13; `07` §4, §17; `10` §3; `08` Milestone 1; `09` §26. Closes G-009, part of G-015.
- Design: DECISIONS.md D-013. Non-goals: cross-Project jobs (none exist before Milestone 12).
- Acceptance: with the service-level Project filter bypassed in a test, a query for another Project's
  rows returns nothing for an authoring request, a public runtime read, and a job; a transaction that has
  not set `app.project_id` reads no Project-owned row; the application role cannot disable the policies;
  tests connect as the application role.

### M0b — Live and mock adapter selection, Catalogue mock

- Spec refs: `07` §16; `10` §3; `08` Milestone 0. Closes G-010.
- Acceptance: `uniche.catalogue.mode=mock` serves the fixture with reference-implementation shapes; the
  application refuses `mock` under the `prod` profile.

### M2b — Application activation

- Spec refs: `03` §2; `07` §14 `settings/application-status`; `08` Milestone 2. Closes part of G-038.
- Acceptance: activation is rejected without an enabled surface or with incomplete default-language translations;
  the transition matrix of `03` §2 holds.
