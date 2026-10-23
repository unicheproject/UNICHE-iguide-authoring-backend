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

### Q-012 — SEO title and description on Points of Interest

- Kind: spec-change-request
- Source: `03` §6 (`PoiTranslation`), `12` §3.
- Finding: the CMS POI editor, built on the 2026-10-01 mockup, has "Search and sharing" fields `seoTitle`
  and `seoDescription` per language; `PoiTranslation` in `03` §6 has neither, so this backend does not
  store them (owner decision 2026-10-14, DECISIONS.md D-020) and the CMS refuses to save them.
- Quoted: "PoiTranslation: id, projectId, poiId, languageCode, title, summary, description,
  instructions, accessibilityInformation".
- Proposed: add `seoTitle` and `seoDescription` (nullable, text) to `PoiTranslation`, with their use by
  the runtime (`11` §3 or a page head rule), or drop the fields from the CMS mockup.
- Assumption taken: not stored until the spec decides. The same applies to `RouteTranslation` (`03` §9),
  whose CMS editor has the same two fields (DECISIONS.md D-021), and to Guides (D-022).

### Q-013 — Category order, icon keys, and how many Categories a Resource has

- Kind: spec-change-request
- Source: `03` §9, §10; `06` §16; `07` §14; `11` §3; `12` §3.
- Finding: `03` §10 gives Category no `sortOrder`, yet `06` §16 and `07` §14 sort Categories by it and
  give them `:order`; "a key of the platform icon set" names no keys; `ResourceCategory` rows and the
  manifest's `categoryIds` allow several Categories per Resource while `03` §9 and `12` §3 say "the
  Category".
- Proposed: add `sortOrder` to Category in `03` §10; list the icon keys (the 20 of DECISIONS.md D-024);
  state that a Point of Interest or Route has at most one Category.
- Assumption taken: all three as proposed (D-024).

### Q-014 — Asset endpoints, the signing host, and the VIDEO usage

- Kind: spec-change-request
- Source: `03` §4; `07` §10, §14; `10` §3, §7; `12` §3.
- Finding: the CMS media library replaces a file, lists where an Asset is used with its role, and
  downloads under the original name, none of which `07` §14 offers; signed URLs must name a host the
  browser reaches, which differs from the backend's endpoint whenever the two sit on different networks
  (any container setup); `03` §4 has no `VIDEO` usage although `12` gives Points of Interest a video.
- Proposed: add `POST .../assets/{id}/file`, `GET .../assets/{id}/usages`, and
  `GET .../assets/{id}/download` to `07` §14; add an optional `iguide.assets.s3.public-endpoint` to the
  `10` §3 contract; add `VIDEO` to the `03` §4 usages; allow a storage service in the local composition
  (`10` §7).
- Assumption taken: all as proposed (D-025).

### Q-015 — Beacon identifier format

- Kind: spec-change-request
- Source: `03` §14; `09` §11.
- Finding: `09` §11 leaves "the beacon identifier format stored in `InteractionTrigger.configuration`"
  open, yet the CMS must store something now.
- Proposed: iBeacon `uuid`, `major` (0–65535), `minor` (0–65535), with `proximityMeters` and
  `cooldownSeconds` (`03` §14 asks for a proximity threshold and a cooldown); one identity per Project.
- Assumption taken: as proposed (D-026).

### Q-016 — Reading a working copy, and the shape of its save

- Kind: spec-change-request
- Source: `07` §14: "`/api/v1/projects/{projectId}/{kind}/{id}/working-copy` PUT, DELETE"; `12` §4:
  "opening the editor of an `ACTIVE` Resource loads its `ResourceWorkingCopy` when one exists".
- Finding: no path reads a copy, and the `PUT` body is described only as "the full payload".
- Proposed: add `GET .../working-copy` returning the copy (`baseVersion`, `version`, `payload`, timestamps,
  `updatedBySubject`) with `resource`, the kind's read DTO as it would read after apply, or `errors` when it
  no longer passes the rules; define the `PUT` body as `{baseVersion, workingCopyVersion, payload}` with the
  rules of D-027.
- Assumption taken: as proposed (D-027).

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
