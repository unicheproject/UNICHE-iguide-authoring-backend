# AGENTS.md — uniche_iguide_backend

Entry point for every human or agent working in this repository. It is the source of truth for how work
is done here. The specification package at `../uniche_iguide_specs` is the source of truth for what is
built.

## 1. Product

UNICHE is a platform through which cultural heritage organizations author and deliver digital visitor
experiences (`README`, `01` §1). iGuide is one UNICHE authoring tool: it lets an organization author a
location-aware visitor Application for `WEB`, `MOBILE`, and `KIOSK` surfaces. It has three components:

- iGuide CMS: Vue.js authoring SPA, sibling repository `../uniche_iguide_frontend`;
- iGuide App: Vue.js visitor runtime (PWA, kiosk mode, Capacitor), separate repository;
- iGuide backend: this repository. Spring Boot service serving the authoring API `/api/v1/**` and the
  public visitor runtime API `/runtime/v1/**`, behind context path `/iguide`. It stores all Project data
  and integrates with the UNICHE platform.

One Catalogue Project = one Application = one Template = one or more enabled Surfaces (`01` §5).

Actors (`12` §2, `02` §2):

- Project member admitted by the Catalogue: full authoring rights (content editor, translator, kiosk
  operator personas) [ARC_6.3];
- organization manager: additionally creates, renames, deletes Projects; the Catalogue enforces it;
- platform admin: the only role that lists and restores deleted Projects (Catalogue behavior, see
  QUESTIONS.md Q-005);
- visitor: anonymous, reads the runtime API only;
- platform services: Keycloak (authenticates), Catalogue (authorizes), VIBE (visitor analytics, runtime
  side), ICAgent and UnicheAIGateway (deferred).

## 2. Authority and reading order

Rules:

- specs win over code. Code that contradicts a spec is a defect and is recorded in GAPS.md;
- statement status follows `STATUS.md`: confirmed fact, decision, working assumption, open question.
  Do not treat prescriptive wording as confirmed;
- a statement marked `[ARC_x.y]` is a D4.3 platform obligation (`README` §Provenance markers);
- the specs are read-only. Never create, edit, or delete files in `../uniche_iguide_specs`. The only
  exceptions so far were owner-instructed (DECISIONS.md D-002, D-008, D-014);
- implementation needs a spec change: add a QUESTIONS.md entry of kind `spec-change-request` with the
  quoted passage, the proposed text, and the reason;
- work can proceed on a stated interpretation: add a DECISIONS.md `spec-interpretation` entry and
  continue;
- the change touches a specs `CLAUDE.md` invariant or an `[ARC_x.y]` statement: stop and get owner
  approval first;
- code that rests on a working assumption names that assumption in a DECISIONS.md entry, so it can be
  found when the assumption changes.

Session reading order:

1. this file;
2. PLAN.md, then the `Now` item's spec references;
3. GAPS.md and QUESTIONS.md entries the item touches;
4. DECISIONS.md entries the item touches;
5. specs as needed: `README`, `STATUS`, `CLAUDE.md`, `01`, then the numbered documents the item cites.
   `d4.3latest.md` is read only at the section an `[ARC_x.y]` marker names.

## 3. Specs baseline

```text
baseline  a792ede3086068f24ffb2c07f608215bee4ec516  (2026-10-02 15:36 +0300)
```

Moved on 2026-10-02 from `1c982b6` to the commit holding the owner-instructed edits (DECISIONS.md D-016).

Every session starts with:

```bash
git -C ../uniche_iguide_specs log --oneline a792ede3086068f24ffb2c07f608215bee4ec516..HEAD
git -C ../uniche_iguide_specs status --short
```

If either prints anything: read the diff, update PLAN.md, QUESTIONS.md, GAPS.md, and TRACEABILITY.md,
then move the baseline here with a DECISIONS.md entry.

## 4. Non-negotiable constraints

Each one is a red line. A change that would cross one stops for owner approval.

1. The Catalogue Project is the tenant; its UUID is the local primary key, never generated locally —
   specs `CLAUDE.md` inv. 1–2, `02` §3 [ARC_6.4, ARC_7.4].
2. Keycloak authenticates, the Catalogue authorizes. JWT signature, exact issuer, expiry, and audience
   `uniche-platform` are validated. Every Project-scoped request calls Catalogue
   `GET /api/v1/projects/{id}` with the forwarded user token before reading or mutating Project data. No
   local data ever grants access, and no fallback authorizes when the Catalogue cannot be reached —
   inv. 3, `02` §1–2, `07` §15 [ARC_7.5].
3. A Project whose Catalogue `tool.slug` is not `uniche.tool.slug` is not an iGuide Project and is
   answered `404` (DECISIONS.md D-009) — `02` §2, inv. 1.
4. No local users, organizations, memberships, roles, or invitations. The only local identity data is
   the Audit Event actor snapshot; the only organization data is `Project.organizationId` — inv. 4,
   `02` §5–7.
5. Every authored entity carries `projectId`; every Project-owned service method and repository query
   takes it explicitly; cross-Project references are impossible through composite `(project_id, id)`
   keys and foreign keys; Row-Level Security is a third layer that replaces neither — inv. 5–6, `06`
   §2, §3, §13.
6. Liquibase owns the schema; `ddl-auto` never exceeds `validate`, tests included; changesets are plain
   SQL with a rollback; a merged changeset is never edited — inv. 14, `06` §15, `10` §5.
7. A controller never calls a Catalogue client or a repository. Application classes are `*UseCase`
   (write), `*Query` (read), `*PassthroughQuery` (read touching no local state) — inv. 15, `07` §2.
8. Errors are RFC 7807 `application/problem+json` with `code` and `requestId` and never a rejected field
   value; the correlation header is `X-UNICHE-Request-Id` on every response — inv. 16, `07` §15, `10`
   §8 [ARC_7.9].
9. No configuration default points at a production system; no secret has a default; required platform
   coordinates fail startup when unset — inv. 17, `10` §2–3 [ARC_9.5].
10. `/runtime/v1/**` answers `Access-Control-Allow-Origin: *` without credentials;
    `iguide.cors.allowed-origins` governs `/api/v1/**` only — inv. 18, `07` §3.
11. No visitor analytics, rating, comment, or session tables and no ingestion endpoint; Audit Events are
    append-only and never carry bearer tokens or secrets — inv. 8, 13, `05` §1, §9 [ARC_4.7].
12. Templates are platform-owned, versioned repository files; applying one materializes copies; nothing
    is live-linked — inv. 7, `04` §2, §5, §10.
13. Resource lifecycle is `DRAFT -> ACTIVE -> ARCHIVED`; `ACTIVE` is never called "published"; an
    `ACTIVE` Resource is edited only through its `ResourceWorkingCopy`; archival is soft — inv. 9–10,
    `03` §1.
14. One translation table per core resource, `(projectId, resourceId, languageCode)` unique; `Instant` and
    `timestamptz` — inv. 11–12, `06` §4, §9.
15. Every persistence test runs against PostgreSQL through Testcontainers with the Liquibase changelog;
    no in-memory database at any scope; tests connect as the application role once RLS exists — `07`
    §17.

## 5. Architecture and stack

Found in the code (DECISIONS.md D-005 to D-007) and specified in `07` and `10`:

- Java 21, Spring Boot 3.4.7 parent as the single version authority, Maven wrapper 3.9.9 — `10` §1;
- modular monolith, feature first, then `api` / `application` / `domain` / `infrastructure` — `07` §1–2;
- packages today: `platform.{catalogue,correlation,security,projectaccess,web,identitysnapshot,
  organization}` and `project`. `identitysnapshot` and `organization` are slated for removal (GAPS.md
  G-005, G-006);
- OAuth2 Resource Server, stateless, CSRF off, no login flow; tokens come from Keycloak through
  Authorization Code + PKCE in the CMS — `02` §1;
- Catalogue client: Spring `RestClient` on the JDK HTTP client, connect `5s`, read `10s`, status
  mapping registered once on the builder — `07` §5;
- PostgreSQL 16, Liquibase formatted SQL under `src/main/resources/db/changelog/sql/`, one file per
  migration group of `06` §15;
- springdoc-openapi 2.8.6 at `/iguide/api-docs`, Actuator `health` and `info` only;
- container: multi-stage Dockerfile, non-root user, healthcheck on `/iguide/actuator/health`; compose
  with `app` and `postgres` — `10` §6.

Request flow target (`07` §3): correlation filter → JWT validation → `UnichePrincipal` → Project id from
path → Catalogue access check (+ tool check) → companion upsert → transaction with
`SET LOCAL app.project_id` → application service → Project-constrained queries → Audit Event.

Platform endpoints (verified live 2026-10-02, DECISIONS.md D-008):

```text
Keycloak issuer   https://idp.uniche-eccch.eu/realms/uniche
Catalogue         https://catalogue.uniche-eccch.eu        (API under /api/v1, Swagger at /swagger-ui)
Portal            https://platform.uniche-eccch.eu         (SPA, never the Catalogue)
CMS client        iguide-web, public, PKCE S256, redirect http://localhost:5173/*
```

Catalogue facts this backend must respect (verified live and in `UNICHEcatalogue` source, 2026-10-02):

- `GET /projects/{id}`: `200` for platform admin, manager of the owning organization, or member; `404`
  otherwise, including soft-deleted; never `403` for a non-member;
- organization project lists are not filtered by tool; `/deleted` lists and `restore` are platform admin
  only;
- the Catalogue error body is `{code, message, correlationId, details[]}` and it correlates only on
  `X-Correlation-Id` (QUESTIONS.md Q-003);
- slugs are `[a-z0-9][a-z0-9-]{1,62}`, unique per organization among live Projects; duplicate is `409`.

Layer sections are added below as slices land: tenancy and RLS, security flow, templates, assets,
runtime API.

## 6. Commands

Only commands that ran on 2026-10-02. Prerequisites: JDK 21, Docker Engine (29.x verified).

| Command | Needs | Result |
|---|---|---|
| `cp .env.example .env` | — | `.env` is gitignored; fill `DB_PASSWORD` |
| `./mvnw -B verify` or `make verify` | Docker for Testcontainers | 70 tests pass, about 30 s |
| `docker compose up --build -d` or `make infra-up` | Docker, `.env` | app healthy after about 85 s on a cold build |
| `curl -s localhost:8080/iguide/actuator/health` or `make health` | running stack | `{"status":"UP"}` |
| `docker compose down -v` or `make infra-down` | — | stack and volume removed |

Not available yet: running from Maven against the compose database (GAPS.md G-012) and an OpenAPI
target (GAPS.md G-004).

## 7. Working method and definition of done

- work one PLAN.md `Now` item at a time, as the smallest vertical slice that meets its acceptance
  criteria;
- the specs `decision-template.md` is the task packet: fill it in the plan or the commit body before
  coding;
- tests land in the same change. Every Project-owned resource family gets cross-Project isolation
  cases: another Project's id in the path, in a body reference, and in a child row;
- Liquibase changesets are immutable once merged; corrections are new changesets;
- never weaken, skip, disable, or silently retry a test to make a build pass;
- file presence is never completion: an item is done when its acceptance criteria run green and the
  evidence is in TRACEABILITY.md;
- per `08` §Companion repositories, a milestone is fully done only when the CMS step (C0–C6) that
  exercises it passes against this backend; until then it is "backend done".

## 8. API contract

The backend owns the authoring and runtime API contract. The frontend consumes it through a generated
client.

- springdoc generates the document; it is committed at `openapi/openapi.yaml` (DECISIONS.md D-004);
- a test in `./mvnw verify` renders the document from the application context and fails when it differs
  from the committed file, so a route or schema change without a regenerated contract fails the build;
- regeneration is one documented command, added with the first slice;
- the frontend reads `../uniche_iguide_backend/openapi/openapi.yaml`; a contract change lands here
  first and is synced into the frontend by a later slice;
- `/runtime/v1` follows the add-only rule of `11` §7 [ARC_5.7].

Status: not built yet (GAPS.md G-004). PLAN.md M0a builds it, because it is the first slice that changes
the API.

## 9. Living documents

| File | Holds | Rule |
|---|---|---|
| `PLAN.md` | `Now` (1–3 items) and `Next` | under 100 lines; never history |
| `DECISIONS.md` | `D-NNN` decisions | append-only; superseded entries stay, a new one supersedes |
| `GAPS.md` | `G-NNN` incompleteness, consequence, closing evidence | ids never reused; closed entries stay with the closing evidence |
| `QUESTIONS.md` | Blocking and Open questions, `spec-change-request`s | resolved only by a DECISIONS.md entry, never by deletion |
| `TRACEABILITY.md` | milestone and D4.3 obligation status | status only from runs that passed, with date and test names |
| `QUICKSTART.md` | first run and daily work | verified commands only |
| `SESSION_BOOTSTRAP_PROMPT.md` | the three short session prompts | — |

Before a session ends, PLAN.md, GAPS.md, and TRACEABILITY.md are truthful.

<!-- Layer sections (tenancy and RLS, security flow, templates, assets, runtime) are appended below as
     slices land. -->
