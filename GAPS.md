# GAPS

Deliberate or found incompleteness, measured against the specs baseline (AGENTS.md §3). Ids are never
reused. A closed gap keeps its entry with the evidence and date that closed it.

Found 2026-10-02 by the bootstrap session (`./mvnw -B verify`, `docker compose up`, live calls against
Keycloak and the Catalogue).

## Open

### G-001 — Error bodies are not RFC 7807

- Found: `platform/web/ErrorEnvelope.java` returns `{type, title, status, requestId, errors}` as
  `application/json`; `type` is a bare code (`RESOURCE_NOT_FOUND`), there is no `code` member, no URN.
- Spec: `07` §15, specs `CLAUDE.md` inv. 16 [ARC_7.9].
- Consequence: the CMS cannot rely on the platform error model; clients would match on `type`.
- Closed by: M0a problem-details tests (every error `application/problem+json`, `code`, `requestId`,
  header equals body).

### G-002 — Correlation header is `X-Request-Id`

- Found: `CorrelationIdFilter.HEADER = "X-Request-Id"`, used inbound, on responses, in CORS, and
  outbound to the Catalogue. The Catalogue ignores it: it correlates on `X-Correlation-Id` only
  (verified 2026-10-02).
- Spec: `10` §8, `07` §5, specs `CLAUDE.md` inv. 16 [ARC_7.9].
- Consequence: platform correlation fails; Catalogue log lines cannot be joined to ours.
- Closed by: M0a correlation tests, response and outbound side (DECISIONS.md D-012).

### G-003 — One CORS configuration for every path

- Found: `SecurityConfig.corsConfigurationSource()` registers one allow-list on `/**`. Verified
  2026-10-02: `GET /iguide/runtime/v1/...` with `Origin: https://museum.example` returns `403 Invalid
  CORS request`.
- Spec: `07` §3, specs `CLAUDE.md` inv. 18, `08` Milestone 0 acceptance.
- Consequence: every web runtime on an organization domain would be blocked.
- Closed by: M0a CORS tests (wildcard on `/runtime/v1`, allow-list on `/api/v1`).

### G-004 — No committed OpenAPI contract and no drift check

- Found: no `openapi/` file; springdoc endpoints require a bearer token (`/iguide/api-docs` → `401`).
- Spec: `10` §8 [ARC_4.8], AGENTS.md §8, DECISIONS.md D-004.
- Consequence: the frontend cannot generate its client; route changes go unnoticed.
- Closed by: M0a: committed `openapi/openapi.yaml`, a failing-on-diff test, a Makefile `openapi` target.

### G-005 — Identity snapshots and JIT provisioning still present

- Found: changeset `iguide:0004-create-identity-snapshots`, `platform/identitysnapshot/*`,
  `JitIdentityProvisioningFilter`. Verified 2026-10-02: one live request stored the caller's subject
  and email in `identity_snapshots`.
- Spec: `02` §6, `07` §3, specs `CLAUDE.md` inv. 4 (removed 2026-10-01).
- Consequence: personal data stored without a use; a write on every request.
- Closed by: M1a drop changeset and a test that a request writes no identity row.

### G-006 — Organization snapshots still present

- Found: changeset `iguide:0003-create-catalogue-organization-snapshots`,
  `platform/organization/*`, called from `CreateProjectUseCase`.
- Spec: `02` §5, `07` §7.
- Consequence: a second organization source; an extra Catalogue call on create.
- Closed by: M1a drop changeset; create makes one Catalogue call.

### G-008 — No tool scoping

- Found: `ProjectAccessService` accepts any Catalogue `200`; `ListOrganizationProjectsPassthroughQuery`
  and the deleted listing return every tool's Projects. Verified 2026-10-02: `GET
  /api/v1/projects/2c1eefc7-…` (a `museotekbox` Project) returned `200` and created a companion row.
- Spec: `01` §5, `02` §2; DECISIONS.md D-009.
- Consequence: iGuide content could be authored into another tool's Project.
- Closed by: M1c tests.

### G-009 — No Row-Level Security

- Found: no policies, one database role (`DB_USERNAME` owns the tables), no `SET LOCAL app.project_id`,
  no isolation tests; no `spring.liquibase.user`.
- Spec: `06` §13, `07` §4, §17, `10` §3, `08` Milestone 1.
- Consequence: Milestone 1 cannot be done; the third isolation layer is absent.
- Closed by: M1b RLS tests.

### G-010 — No live or mock adapter selection

- Found: no `uniche.catalogue.mode`, no fixture, no `prod` refusal.
- Spec: `07` §16, `10` §3, `08` Milestone 0.
- Consequence: local runs and the CMS need the live Catalogue and real users.
- Closed by: M0b tests.

### G-011 — Local composition has two services

- Found: `compose.yaml` has `app` and `postgres`; no `cms`, `runtime`, `vibe-mock`. Its header comment
  still says asset bytes live on the filesystem, contrary to `10` §7.
- Spec: `10` §6, `08` Milestone 0; topology decided in DECISIONS.md D-010.
- Consequence: no local preview; Milestone 0 acceptance not met.
- Closed by: M0c for the backend services; the frontend profiles when those images exist (D-010).

### G-012 — App cannot run from Maven against the compose database

- Found: the compose `postgres` service publishes no host port. Verified 2026-10-02: `./mvnw
  spring-boot:run` with `.env` reached another PostgreSQL on `localhost:5432` and failed authentication.
  The README "without Docker" path is therefore unverified.
- Spec: `10` §6.
- Consequence: no fast edit-run loop; no Makefile `run` target.
- Closed by: M0c, a verified `make run`.

### G-013 — No CI workflow

- Found: no `.github/workflows`.
- Spec: AGENTS.md §8 (contract diff), `07` §16 (contract tests every build).
- Consequence: nothing enforces `./mvnw verify` on push.
- Closed by: a workflow running `./mvnw -B verify` green on the default branch.

### G-015 — Property contract incomplete

- Found: absent from `application.properties`: `spring.liquibase.user` and `password`,
  `uniche.catalogue.mode`, `uniche.catalogue.mock-fixture`, `springdoc.api-docs.enabled`,
  `iguide.runtime.minimum-version`. The other rows of `10` §3 belong to later milestones and are not
  declared on purpose (rule 6, no dead configuration).
- Spec: `10` §3.
- Closed by: M1b (Liquibase roles), M0b (mode, fixture), M0a (springdoc), M8 (runtime).

### G-016 — Catalogue timestamps not mapped

- Found: `CatalogueProjectDto` has no `createdAt` and `updatedAt`; a comment in `ProjectAccessService`
  says the Catalogue does not return them. It does (live `ProjectResponse`, 2026-10-02).
- Spec: `02` §4, §9.
- Closed by: M1c test that the companion row carries both values.

### G-017 — Local slug rule differs from the Catalogue

- Found: `CreateProjectRequest.slug` is `[a-z0-9]+(-[a-z0-9]+)*`, max 100; the Catalogue accepts
  `[a-z0-9][a-z0-9-]{1,62}`.
- Consequence: slugs of 64–100 characters pass here and fail upstream with `400`, which this backend
  reports as `502`.
- Closed by: M1c validation test at the Catalogue's bounds.

### G-018 — Stale migration-group comment

- Found: `db.changelog-master.xml` lists groups "6. audit and analytics" and "7. derived statistics".
- Spec: `06` §15 (nine groups, statistics removed).
- Closed by: M1a.

### G-019 — Catalogue `400` and `500` surface as `502` or `503`

- Found: `RestClientCatalogueClient.mapError` maps unexpected statuses to a bad-response exception and
  `5xx` to unavailable. The Catalogue answers `400 VALIDATION_FAILED` on invalid bodies and `500` for a
  malformed UUID, an unreadable body, or an unknown route (`UNICHEcatalogue` `GlobalExceptionHandler`).
- Consequence: a client mistake reported as a platform outage. Mostly avoided by validating locally
  first (G-017).
- Closed by: a decided mapping for upstream `400` (QUESTIONS.md Q-009) and its test.

### G-020 — No CMS companion step proven

- Found: `../uniche_iguide_frontend` has a Vue scaffold and no generated client.
- Spec: `08` §Companion repositories (C0 after Milestone 1).
- Consequence: no milestone can be fully done.
- Closed by: C0 passing against this backend.

### G-021 — No organizations passthrough

- Found: no `GET /api/v1/organizations`; the CMS picker cannot name organizations without calling the
  Catalogue itself.
- Spec: `02` §11, `07` §14, `12` §7 (amended 2026-10-02, D-014).
- Closed by: M1c test that the endpoint relays the Catalogue body and touches no local state.

### G-032 — Assets: runtime delivery, processing, and the rest of Milestone 3

- Found (M3, 2026-10-16):
  - no `GET /runtime/v1/projects/{p}/assets/{id}` redirect (`11` §2), since the runtime API does not exist
    (Milestone 8);
  - no `AI_OUTPUT_UNREVIEWED` warning or `CONTENT_REVIEWED` action on `provenance` (`08` Milestone 3);
    the column exists on every translation table but nothing writes or reads it;
  - `durationSeconds` is never measured for audio and video; no virus scanning, transcoding, or resumable
    upload (`09` §2); objects of replaced files and of failed metadata writes are never cleaned up;
  - no theme references to Assets (Milestone 2), so an Asset used by a theme is not kept in use;
  - an ACTIVE Resource cannot change its media until working copies exist (G-027).
- Closed by: Milestones 2, 8, and 13, and a storage cleanup job.

### G-033 — Triggers: QR image, capabilities, list chips, other targets

- Found (M5t, 2026-10-19):
  - `GET .../triggers/{id}/qr.png` always answers `422 NO_PUBLIC_BASE_URL`: the `WEB` surface's
    `publicBaseUrl` does not exist (Milestone 2), and no QR image library is wired;
  - the `QR_TRIGGERS` and `LOCATION_TRIGGERS` capabilities are not checked (G-024);
  - triggers target Points of Interest only; Places, Routes, Guides, Events, and Pages follow;
  - trigger changes append no Audit Event (G-023) and are not in a manifest (Milestone 8).
- Closed by: Milestones 2, 7, 8, and the editors of the other kinds.

## Closed

### G-007 — `/me/authorization` is cached and reshaped

- Found: `MeAuthorizationQuery` caches per subject (not issuer) for the Catalogue TTL with
  `If-None-Match`; `MeAuthorizationResponse` renames `managedOrganisations` to `managedOrganizations` and
  drops `etag` and `ttlSeconds`.
- Spec: `02` §10 (uncached passthrough, Catalogue body unchanged).
- Consequence: stale authorization view up to 45 s after a membership change.
- Closed 2026-10-13 (M1a, `/me/authorization` part): `MeAuthorizationPassthroughQuery` calls the
  Catalogue on every request and the endpoint returns the Catalogue's fields and names
  (DECISIONS.md D-017). Evidence: TRACEABILITY.md R4, `MeAuthorizationIntegrationTest`
  (`asksTheCatalogueOnEveryRequest`, `returnsTheCatalogueBodyInTheCataloguesShape`). Found by the CMS:
  sign-in crashed on `authorization.managedOrganisations is not iterable` (frontend GAPS.md G-017).

### G-014 — Dockerfile entrypoint differs from `10` §6 item 5

- Found: `ENTRYPOINT ["java", "-jar", "application.jar"]`, not `JarLauncher` (DECISIONS.md D-006).
- Consequence: none at run time; the spec text is wrong for Boot 3.4 extraction. QUESTIONS.md Q-007.
- Closed 2026-10-02 by DECISIONS.md D-014: `10` §6 item 5 now names the `java -jar` entrypoint.

### G-022 — No Row-Level Security on the content and spatial tables

- Found: `project_languages`, `places`, `place_translations`, `geographic_maps`,
  `geographic_map_resources`, `floor_plans`, `floor_plan_positions` have no policy (`06` §13).
- Consequence: isolation rests on Project-scoped queries and composite foreign keys only.
- Closed by: M1b, adding a policy per table in a new changeset.

### G-023 — Content writes append no Audit Event

- Found: create, update, status, reorder, and placement writes record only `updated_by_subject`.
- Spec: `05` §3 (`RESOURCE_CREATED`, `RESOURCE_UPDATED`, ..., `RESOURCE_REORDERED`, `LANGUAGE_ADDED`).
- Closed by: Milestone 7.

### G-024 — No ProjectSettings and no `contentRevision`

- Found: the default language lives only in `project_languages.is_default` (DECISIONS.md D-019); no
  runtime-visible change increments `contentRevision`; there is no Project timezone to render event
  occurrences in and no `featureConfiguration`, so the `EVENTS` capability is never checked (D-023).
- Spec: `03` §2, `11` §4. Closed by: M2 (settings) and M8 (revision upkeep).

### G-025 — Floor Plan image is not an Asset

- Found: `floor_plans.asset_id` is nullable, has no foreign key, and is never checked (DECISIONS.md D-018).
- Spec: `03` §8 (`assetId`), `07` §14 (Asset references list Floor Plans).
- Closed by: Milestone 3, with a changeset adding the foreign key and the `IMAGE`/`FLOOR_PLAN` check.
- Closed 2026-10-16 (M3): changeset 0031 adds the composite foreign key; a plan's image must be an
  `IMAGE` or `FLOOR_PLAN` Asset of the Project that is not archived and keeps it in use (DECISIONS.md
  D-025). Evidence: TRACEABILITY.md R11, `AssetIntegrationTest.aFloorPlanImageIsCheckedAndKeepsTheAssetInUse`,
  `FloorPlanIntegrationTest.activationNeedsAnImage`.

### G-026 — Generic Resource endpoints missing for Places, Maps, and Floor Plans

- Found: no `translations/{lang}`, `resource-assets`, `categories`, child `:order` endpoints; no
  `pending`, `language`, or `sort` list parameters.
- Spec: `07` §14. Closed by: Milestones 3, 4, and 8 as their entities land.

### G-027 — No working copies

- Found: `ACTIVE` Places, maps, and plans are not editable; `hasWorkingCopy` is always `false`.
- Spec: `03` §1, `06` §17, `07` §14. Closed by: Milestone 8.

### G-028 — Points of Interest do not exist

- Found: placements accept only `PLACE`; no POI counts toward a Place's references.
- Spec: `03` §6, §7, §8. Closed by: Milestone 4 (POIs).
- Closed 2026-10-14 (M4q): POIs exist and can be placed on maps and floor plans (DECISIONS.md D-020).
  Evidence: TRACEABILITY.md R6, `PoiIntegrationTest`. Still open elsewhere: Routes, Guides, Events,
  and Triggers as referrers (their milestones), POI Categories (G-029), POI media (G-025).

### G-029 — Categories do not exist

- Found: map `visibility.*.hiddenCategoryIds` must be empty (`422` otherwise).
- Spec: `03` §7, §10. Closed by: Milestone 4 (Categories).
- Closed 2026-10-15 (M4c): Categories, ResourceCategory bindings for POIs and Routes, and map hidden
  Categories validated by scope (DECISIONS.md D-024). Evidence: TRACEABILITY.md R10, `CategoryIntegrationTest`.

### G-030 — No readiness codes for Routes

- Found: `03` §1 makes "at least one Route Stop whose Point of Interest is `ACTIVE`" a required field and
  "a Route with fewer than two stops" a warning, but the closed code lists of `07` §14 have no code for
  either. Activation enforces the first as `MISSING_REQUIRED_FIELDS` at path `stops`; the readiness
  endpoint does not exist yet.
- Consequence: the readiness response (Milestone 8) cannot report these without a spec code.
- Closed by: a QUESTIONS.md spec-change-request with the readiness milestone.

### G-031 — Rich text cannot embed images

- Found: Guide TEXT blocks refuse `image` nodes (DECISIONS.md D-022), because their Assets would need
  `INLINE` bindings (`03` §4, `07` §14) and Assets do not exist (G-025).
- Closed by: Milestone 3, extracting `assetId`s on every save into `INLINE` `ResourceAsset` rows.
- Closed 2026-10-16 (M3): TEXT blocks take `asset` nodes; every block write reconciles the Guide's INLINE
  rows (DECISIONS.md D-025). Evidence: TRACEABILITY.md R11,
  `ResourceAssetIntegrationTest.imagesInGuideTextBecomeInlineBindingsThatFollowEverySave`.
