# QUESTIONS

An entry is resolved only by a DECISIONS.md entry that names it; it is then marked
`Resolved by D-NNN` and stays. Kinds: `question`, `spec-change-request`. `09` items are cited, not copied.

## Blocking

None open. Q-002 and Q-003 were resolved on 2026-10-02 and are kept here for reference.

### Q-002 — Row-Level Security: companion upsert ordering and role provisioning

- Kind: question
- Source: `07` §3 orders "Project companion upserted" before "transaction opened, SET LOCAL
  app.project_id"; `06` §13 gives `projects` its own policy on `id`; `09` §26 leaves role provisioning
  per environment open.
- Question: does the companion upsert run inside a transaction that first sets `app.project_id` to the
  Catalogue-confirmed id? How are the migration and application roles created locally, in Testcontainers,
  and in deployed environments, and who holds their credentials?
- Consequences: setting the id before the upsert keeps one policy for `projects`; a separate role for the
  upsert widens privileges; role creation inside a Liquibase changeset needs a superuser at migration
  time.
- Recommendation: one transaction per access check that sets `app.project_id` before the upsert; roles
  created by the migration role in group 1 for local and test, by operations elsewhere.
- Resolved by D-013 (2026-10-02). `09` §26 still shapes Milestone 12.

### Q-003 — Correlation header towards the Catalogue

- Kind: spec-change-request
- Source: `07` §5: "propagate the `X-UNICHE-Request-Id` correlation header [ARC_7.9]".
- Finding: the Catalogue reads and echoes only `X-Correlation-Id` (`UNICHEcatalogue`
  `common/CorrelationIdFilter.java`; verified live 2026-10-02: a sent `X-UNICHE-Request-Id` was ignored and
  a fresh `correlationId` returned).
- Proposed text: "propagate the request id as `X-UNICHE-Request-Id` and, until the Catalogue adopts the
  platform header, also as `X-Correlation-Id`."
- Reason: without it no Catalogue log line can be joined to an iGuide request. The statement carries
  `[ARC_7.9]`, so owner approval is required (AGENTS.md §2). Alternatively the Catalogue adopts
  `X-UNICHE-Request-Id`.
- Resolved by D-012 (2026-10-02): both headers; spec text applied to `07` §5 by D-014.

## Open

### Q-001 — Local composition: how many services, and where do the CMS and runtime images come from?

- Kind: question
- Source: `10` §6 says "Four containers" and lists `app`, `cms`, `runtime`, `vibe-mock`, `postgres`;
  `08` Milestone 0 says "the four-service local composition, including the CMS and runtime static
  containers".
- Question: is `vibe-mock` part of this repository's compose? Are `cms` and `runtime` built from the
  sibling repositories (build context `../uniche_iguide_frontend`), pulled as published images, or left
  to their own repositories' compose files?
- Consequences: building from sibling paths couples this repository's compose to the workspace layout;
  published images need a registry; leaving them out breaks the `08` Milestone 0 criterion as written.
- Resolved by D-010 (2026-10-02).

### Q-004 — Tool scoping of Catalogue Projects

- Kind: spec-change-request
- Source: `02` §2 step 4: "if Catalogue returns `200`, continue".
- Proposed text: "if Catalogue returns `200` and the Project's `tool.slug` equals `uniche.tool.slug`,
  continue; a Project of another tool is answered like a Catalogue `404`, without touching the companion
  row. Organization listings keep only Projects of this tool."
- Reason: DECISIONS.md D-009. Work proceeds on that interpretation (M1c).
- Resolved by D-014 (2026-10-02): applied to `02` §2 and §11.

### Q-005 — Deleted Projects and restore are platform-admin only

- Kind: spec-change-request
- Source: `12` §3 Project picker: "shows soft-deleted Projects of a managed organization in a separate
  list with a restore action"; `12` §6 "links to the deleted list of the organization for restore".
- Finding: the Catalogue allows `GET /organisations/{orgId}/projects/deleted` and `POST
  /projects/{id}/restore` to platform admins only; a manager receives `403` (`UNICHEcatalogue`
  `ProjectService`, verified live 2026-10-02 with a manager token).
- Proposed text: the picker shows the deleted list and the restore action only when
  `/me/authorization` reports `platformAdmin`; a manager is pointed to the Portal or a platform admin.
- Reason: otherwise the CMS offers an action that always fails. Affects C0, not the backend code.
- Resolved by D-011 (2026-10-02): deferred for the MVP; no deleted list or restore in the CMS.

### Q-006 — Invitations: does the D4.3 divergence still hold?

- Kind: question
- Source: `02` §14, `09` §13 [ARC_4.3 divergence].
- Finding: the Catalogue now exposes `POST /api/v1/projects/{projectId}/invitations` (manager only,
  returns a one-time token, no email sent) and accept endpoints.
- Question: should iGuide offer FS-AUT-14 as a Catalogue passthrough with no local state, as `09` §13
  foresees if WP7 insists?
- Consequences: a passthrough is small; without email delivery the CMS must show or copy the token.
- Resolved by D-011 (2026-10-02): deferred for the MVP; the divergence stands.

### Q-007 — Dockerfile entrypoint

- Kind: spec-change-request
- Source: `10` §6 Image item 5: "entrypoint on the layered `JarLauncher`".
- Proposed text: "entrypoint on the extracted application jar (`java -jar application.jar`), whose
  manifest class path names the dependency layers."
- Reason: Boot 3.4 `jarmode=tools extract --layers` leaves the loader layer empty, so `JarLauncher` does
  not resolve (commit `9323499`); DECISIONS.md D-006, GAPS.md G-014.
- Resolved by D-014 (2026-10-02): applied to `10` §6.

### Q-008 — HTTP client wording

- Kind: spec-change-request
- Source: `07` §5: "Use `WebClient` or Spring HTTP Interface clients."
- Proposed text: "Use Spring `RestClient`, optionally behind an HTTP Interface."
- Reason: DECISIONS.md D-005.
- Resolved by D-014 (2026-10-02): applied to `07` §5.

### Q-009 — Mapping of Catalogue `400` and `500`

- Kind: question
- Source: `07` §15 status table (no row for an upstream `400`); GAPS.md G-019.
- Question: is an upstream `400 VALIDATION_FAILED` returned as `422 VALIDATION_ERROR` with the
  Catalogue's field paths, or always prevented by local validation and treated as `502` when it happens?
- Recommendation: validate locally to the Catalogue's rules; map an upstream `400` to `422` with the
  `details` paths (never values), so the author can act.
- Shapes M0a and M1c.

### Q-010 — Catalogue URL in the specs

- Kind: spec-change-request
- Source: `10` §4 and specs `CLAUDE.md` named `https://platform.uniche-eccch.eu/` as the Catalogue.
- Resolved by D-008 (owner-instructed edit, uncommitted in the specs repository).

### Q-011 — Keycloak clients for staging and production

- Kind: question
- Source: `02` §1, `10` §3, `12`.
- Finding: `iguide-web` exists on the live realm (public, PKCE `S256`, redirect `http://localhost:5173/*`,
  web origin accepted for `http://localhost:5173`, tokens carry `aud` `uniche-platform`; verified
  2026-10-02). It is absent from the `UNICHEidp` realm export.
- Question: which CMS origins are added for staging and production, and is the realm export updated?
  The confidential `iguide-svc` client of `10` §3 is needed only from Milestone 12.
- Assumption taken: local development only. Blocks nothing in the backend; C0 deployment needs it.

### Open decisions of `09` that shape the next milestones

| `09` | Shapes or blocks |
|---|---|
| §26 RLS roles and cross-Project jobs | M1b (see Q-002), Milestone 12 |
| §19 typography ownership | Milestone 2 `themeValues`, style presets |
| §20 region as a data dimension | Milestone 2 seed templates (`multi-region-network`) |
| §27 Page component text localization | Milestone 2 initial Pages, Milestone 6 |
| §2 asset processing (GLB, signed URL lifetime, upload size) | Milestone 3 |
| §22 map tiles and geocoding | Milestone 5 `runtimeConfiguration` |
| §25 floor plan labels | Milestone 5 |
| §12 runtime field names, PID registration | Milestone 8 |
| §8 runtime compatibility policy | Milestone 8 |
| §1 deployment package generation | Milestone 11 |
| §15 ICAgent contract | Milestone 12 |
| §16 AI gateway | Milestone 13 |
| §18 OpenAPI collection, horizontal SDKs | contract publication; DECISIONS.md D-004 proceeds |
| §28 surfaces of the first pilot | QR codes, mobile-only features |
