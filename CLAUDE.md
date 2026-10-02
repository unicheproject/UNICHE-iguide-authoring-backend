# CLAUDE.md

Read `AGENTS.md` first. It is authoritative for this repository; this file only adds Claude Code
specifics.

- Use plan mode for any slice that touches more than a couple of files, a Liquibase changeset, security
  configuration, or the OpenAPI contract.
- Cite spec sections in plans and decisions as `NN` §N (for example `02` §6, `08` Milestone 2). The
  specs live at `../uniche_iguide_specs`.
- Never edit `../uniche_iguide_specs`. Proposed changes go to `QUESTIONS.md` as `spec-change-request`.
- Never commit unless asked. Commit messages carry no AI attribution: no `Co-Authored-By`, no
  "Generated with" lines.
- Commit title is `TASK-ID: outcome` (for example `M0a: RFC 7807 problem details and split CORS`),
  followed by a body grouped by area (schema, security, API, tests, docs) and ending with a
  `Verification:` section listing the commands run and their results.
- Before ending a session, make `PLAN.md`, `GAPS.md`, and `TRACEABILITY.md` truthful.
