# TRACEABILITY

Status comes only from runs that passed. Values: `not started`, `in progress`, `backend done` (acceptance
criteria of `08` green here), `done` (also exercised by its CMS step). Evidence names the dated command
and the test classes or methods.

## Evidence runs

| Id | Date | Command | Result |
|---|---|---|---|
| R1 | 2026-10-02 | `./mvnw -B verify` | BUILD SUCCESS, 70 tests, 0 failures, 0 skipped, 30 s |
| R2 | 2026-10-02 | `docker compose up --build -d`, `curl /iguide/actuator/health` | healthy at 84 s, `{"status":"UP"}`; unauthenticated `/api/v1/me/authorization` → `401` with `WWW-Authenticate: Bearer` |
| R3 | 2026-10-02 | R2 with `CATALOGUE_BASE_URL=https://catalogue.uniche-eccch.eu`, live Keycloak tokens (`dev-cli` and `iguide-web` PKCE) | see "Live integration" below |

Live integration (R3), against the deployed Keycloak and Catalogue:

- `GET /api/v1/me/authorization` → `200` (body reshaped, GAPS.md G-007);
- `GET /api/v1/organizations/{Test Museum}/projects` → `200`, every tool's Projects (G-008);
- `GET /api/v1/projects/{museotekbox Project}` → `200` and a companion row (G-008);
- `GET /api/v1/projects/{unknown}` → `404`; `/deleted` as manager → `403`, as platform admin → `200`;
- write path as platform admin: create → `201` with Catalogue `tool.slug = iguide`; duplicate slug →
  `409`; read → `200`; `PATCH` name and local description → `200`; delete → `204` and companion
  soft-deleted; read after delete → `404`; restore → `200`; delete → `204`. The test Project
  `f4216c57-23c4-4875-8e54-7d42b7f5449e` ("iGuide integration test (renamed)", slug
  `iguide-integration-test-20261002`) is left soft-deleted in the Catalogue.

## Milestones (`08`)

| Milestone | Outcome | Key specs | Status | Evidence | Exercised by |
|---|---|---|---|---|---|
| M0 Scaffold | buildable service, property contract, Liquibase, containers, correlation, errors, CORS, mocks | `10`, `07` §3, §15, §16 | in progress | R1: `PlatformConfigurationCheckTest`, `CataloguePropertiesTest`, `SchemaAndMappingIntegrationTest`, `CorrelationIdFilterTest`; R2. Missing: G-001, G-002, G-003, G-010, G-011 | C0 |
| M1 Platform and tenancy | Keycloak validation, Catalogue authorization, Project lifecycle, companion row, RLS | `02`, `06` §13, `07` §4–7 | in progress | R1: `AudienceValidatorTest`, `RestClientCatalogueClientTest`, `ProjectAccessServiceTest`, `ProjectLifecycleIntegrationTest` (404 without access, local row never grants access, 404 soft-deletes, 5xx and timeout change nothing, 503, 504); R3. Missing: G-005 to G-009 | C0 |
| M2 Templates and initialization | seeded Templates, `POST /initialize`, settings, languages, surfaces, theme | `04`, `03` §2–3 | not started | — | C1 |
| M3 Assets and localization | S3 assets, upload limits, translations, provenance, slugs | `03` §1, §4, `07` §10, `10` §7 | not started | — | C2 |
| M4 Core visitor content | Places, POIs, Categories, Guides, lifecycle, references | `03` §5–6, §10–11 | not started | — | C3 |
| M5 Routes and spatial | Routes, Stops, Maps, Floor Plans, Triggers, QR | `03` §7–9, §14 | not started | — | C3 |
| M6 Pages and Navigation | flat Pages and components, `PRIMARY` and `FOOTER` | `03` §12–13, `07` §9 | not started | — | C4 |
| M7 Auditing | immutable Audit Events, query API | `05` §2–6 | not started | — | C6 |
| M8 Runtime, preview, working copies, readiness | manifest, ETag, preview, apply, readiness, statistics | `11`, `03` §1, `07` §13–14 | not started | — | C5, A0, A1 |
| M9 Events | Events, occurrences, manifest section | `03` §15 | not started | — | C6, A2 |
| M10 Custom entry types | — | `03` §16 | deferred | `08` | — |
| M11 Deployment packages | entity, versioning, storage, download, `stale` | `04` §13, `07` §11 | not started | — | C6, A3 |
| M12 ICAgent ingestion | outbox, chunking, worker | `07` §18 | not started (deferred until the ICAgent API exists) | — | A4 |
| M13 AI translation | gateway translation and review | `08` Milestone 13 | not started (deferred until the gateway API exists) | — | — |
| Deferred list | `08` §Deferred milestones | — | not started | — | — |

## D4.3 obligations owned by the backend (`01` §3)

| Obligation | Where specified | Status | Evidence or gap |
|---|---|---|---|
| FS-IAM-01, 04, 05: single realm, Catalogue-held roles, standard token | `02` §1–2 | in progress | R1 `AudienceValidatorTest`, `ProjectLifecycleIntegrationTest`; R3 tokens carry no roles |
| FS-AUT-02, FS-API-01: project-scoped workspace and URLs | `02` §3, `07` §14 | in progress | R1 project paths; G-008, G-009 |
| FS-AUT-03: create, edit, preview, publish | `03` §1, `11` §2 | not started | M4, M8 |
| FS-AUT-04, FS-API-03: runtime API minimum fields | `11` §2–3 | not started | M8 |
| FS-AUT-05: experience PID | `11` §3 | not started | M8; `09` §12 |
| FS-AUT-06: publication events to ICAgent | `07` §18 | not started (deferred) | M12 |
| FS-AUT-07, FS-AI-04, NFS-AIE-01 to 03: reviewable AI outputs | `03` §1 | not started | M3 (`provenance`), M13 |
| FS-AUT-08: template mechanism | `04` | not started | M2 |
| FS-AUT-09, NFS-MNT-04: horizontal SDKs | `07` ports | open | `09` §18 |
| FS-AUT-13: experience builder | `04` §13 | not started | M11 |
| FS-AUT-14, FS-IAM-03: invitations through the Catalogue | `02` §14 | deferred for the MVP | DECISIONS.md D-011 |
| FS-AUT-15, FS-VIS-06: multilingual | `03` §3, `11` §3 | not started | M2, M3, M8 |
| FS-AUT-16, NFS-USA-02: WCAG 2.1 AA (backend validation) | `14` §2 | not started | M2 contrast, M8 readiness |
| FS-ASM-04 to 06: asset import and provenance | `09` §14 | open | — |
| FS-VIS-01, NFS-INT-05, NFS-SCL-03: latest content, caching, ETags, language negotiation | `11` §2, §4, §5 | not started | M8 |
| FS-IAM-07, NFS-SEC-07: audited administrative actions | `05` §2–6 | not started | M7 |
| FS-API-02, NFS-INT-01: OpenAPI published | `10` §8 | not started | G-004 |
| FS-API-04, NFS-OBS-01: RFC 7807 and request correlation | `07` §15, `10` §8 | in progress | R1 `CorrelationIdFilterTest`; G-001, G-002 |
| FS-API-05, NFS-SEC-04: client credentials for service calls | `10` §3 | not started | M12 |
| NFS-AVL-05, NFS-DEP-01, NFS-DEP-02: health, containers, externalised configuration | `10` §2, §6, §8 | in progress | R2 health; R1 `PlatformConfigurationCheckTest`; G-011 |
| NFS-SEC-01 to 03, 05, 06: HTTPS, OIDC, project-scoped access, data minimisation | `02`, `05` §7 | in progress | R1, R3; G-005 (identity data), G-009 |
| Section 9.6: backups and monitoring | `10` §9 | deferred | `08` deferred list |
| NFS-PER-01, 02: responsiveness targets | `08` | deferred | `08` deferred list |
