# UNICHE iGuide Authoring Tool Backend

## Status and where to start

Read [`AGENTS.md`](AGENTS.md) first, then [`PLAN.md`](PLAN.md) for the current work. Verified commands
are in [`QUICKSTART.md`](QUICKSTART.md); known gaps between this code and the specification are in
[`GAPS.md`](GAPS.md).

Spring Boot backend for the iGuide UNICHE authoring tool. The authored output is a visitor
experience application — web, mobile, or kiosk — built from a versioned template.

## Authoritative design

The specification package is the design of record. This repository implements it; where the two
disagree, the specification is right and the code is a defect.

Non-negotiable invariants, restated because they shape every class here:

1. the UNICHE Catalogue Project is the tenant boundary, not the organization;
2. the Catalogue Project UUID is the local Project primary key;
3. Keycloak authenticates, the Catalogue authorizes — a valid token proves identity only;
4. local user and organization records are non-authoritative snapshots;
5. every authored entity carries `UUID projectId`, and every project-owned service method and
   repository query takes project context explicitly;
6. cross-project references are impossible by construction, in services and in the schema;
7. Liquibase owns the schema — `ddl-auto` never exceeds `validate`, in any environment including
   tests;
8. a controller never calls a Catalogue client or a repository directly;
9. error bodies carry `requestId` and never a rejected field value;
10. no configuration default points at a production system, and no secret has a default at all.

## Platform dependencies

Both are already deployed and are consumed as remote services. They are never run locally.

```text
Keycloak realm    https://idp.uniche-eccch.eu/realms/uniche
UNICHE Catalogue  https://catalogue.uniche-eccch.eu/
```

Token audience is `uniche-platform`. The client obtains tokens directly from Keycloak with
Authorization Code plus PKCE; this backend is never part of a login flow.

## Running locally

```bash
cp .env.example .env      # then fill in DB_PASSWORD and confirm the platform URLs
docker compose up --build
curl -i localhost:8080/iguide/actuator/health
```

Two containers only: this application and PostgreSQL. Liquibase runs at application startup.

Without Docker, provide a PostgreSQL instance and run:

```bash
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run
```

The application refuses to start without an explicit `IDP_ISSUER_URI` and `CATALOGUE_BASE_URL`.

## Testing

```bash
./mvnw verify
```

Every test that touches persistence runs against PostgreSQL through Testcontainers, with the
Liquibase changelog creating the schema. There is no in-memory database at any scope.

## Layout

Feature first, then layer. Each feature holds `api`, `application`, `domain`, and `infrastructure`
where practical. Application classes are named for what they do:

| Suffix | Meaning |
|---|---|
| `*UseCase` | a write operation |
| `*Query` | a read operation |
| `*PassthroughQuery` | a read that touches no local state at all |

## Before implementing a change

Complete the checklist in `decision-template.md` of the specification package: what is implemented,
which requirement it covers, which files change, what changes in the database, which project-scoping
constraint it touches, what API contracts change, what the frontend needs, and what it depends on in
the Catalogue or the IdP.
