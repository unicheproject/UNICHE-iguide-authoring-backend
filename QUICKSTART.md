# QUICKSTART

Every command here ran on 2026-10-02. AGENTS.md §6 is the command table.

## Prerequisites

- JDK 21 (Temurin 21.0.12 verified);
- Docker Engine with Compose (29.6 verified); Testcontainers needs the daemon;
- network access to `https://idp.uniche-eccch.eu` and `https://catalogue.uniche-eccch.eu` for running the
  stack; the test suite needs neither.

Keycloak and the Catalogue are remote services and are never run locally.

## First run

```bash
make setup        # copies .env.example to .env if .env is missing; then set DB_PASSWORD in .env
make verify       # ./mvnw -B verify: build and all tests, about 30 s
make infra-up     # docker compose up --build -d, waits for the health check
make health       # {"status":"UP"}
```

`.env` is gitignored. Its platform values come from `.env.example`:

```text
IDP_ISSUER_URI=https://idp.uniche-eccch.eu/realms/uniche     # byte-identical to the token issuer
CATALOGUE_BASE_URL=https://catalogue.uniche-eccch.eu         # no /api/v1 suffix; not the Portal host
```

## Calling the API by hand

A token for a test user comes from the live realm. Credentials are held by the project owner and never
committed.

```bash
TOKEN=$(curl -s https://idp.uniche-eccch.eu/realms/uniche/protocol/openid-connect/token \
  -d grant_type=password -d client_id=dev-cli \
  -d username="$TEST_USER" --data-urlencode password="$TEST_PASSWORD" | jq -r .access_token)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/iguide/api/v1/me/authorization
```

Tokens live 300 s. The CMS uses the public client `iguide-web` with PKCE from `http://localhost:5173`.
Writes go to the live Catalogue; create test Projects only in the `Test Museum` organization.

## Daily work

```bash
make verify       # before and after every change
make infra-up     # rebuilds the image from the working tree
make infra-down   # docker compose down -v: stops the stack and drops the database volume
```

## Stopping

```bash
make infra-down
```

## Not available yet

- running the application from Maven against the composed database (GAPS.md G-012);
- an `openapi` target (GAPS.md G-004, PLAN.md M0a).
