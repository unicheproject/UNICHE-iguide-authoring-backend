# Session bootstrap prompts

Paste one of these to start a session in `uniche_iguide_backend`.

## 1. Implement the current `Now` item

```text
Work in uniche_iguide_backend. Read AGENTS.md, then PLAN.md.
First run the specs baseline check of AGENTS.md §3; if it prints anything, update PLAN, QUESTIONS, GAPS,
and TRACEABILITY and move the baseline with a DECISIONS entry before anything else.
Then implement the first unfinished PLAN.md `Now` item as one vertical slice: fill the specs
decision-template.md in your plan, use plan mode, write the tests with the code (including cross-Project
isolation cases), and meet every acceptance criterion with `make verify` green.
Record decisions in DECISIONS.md, close or open entries in GAPS.md and QUESTIONS.md, put dated evidence in
TRACEABILITY.md, and leave PLAN.md truthful. Do not commit unless I ask.
```

## 2. Bounded review of a finished slice

```text
Work in uniche_iguide_backend. Read AGENTS.md. Review the slice <TASK-ID> (diff: <commit range or
working tree>) against its PLAN.md acceptance criteria and the specs it cites. Change nothing; report
findings with file:line and the spec section, in four separate passes:
1. correctness against the specs and the acceptance criteria;
2. Project isolation and security: another Project's id in path, body, and child rows; Catalogue
   denial (404 for non-members, 403 for manager-only operations, no local fallback); tool scoping;
   token issuer and audience; no rejected values in errors; no secrets in logs or audit;
3. tests: every criterion has a test that would fail without the change; no weakened, skipped, or
   retried test; persistence tests on Testcontainers as the application role;
4. API contract: openapi/openapi.yaml regenerated and diff test green; breaking changes named;
   frontend impact stated.
End with a verdict per pass and the GAPS.md entries the review would add.
```

## 3. Prepare the questions blocking a milestone

```text
Work in uniche_iguide_backend. Read AGENTS.md, QUESTIONS.md, and PLAN.md. For milestone <ID>, list
every QUESTIONS.md entry and every 09-open-decisions.md item that blocks or shapes it. For each, give the
plausible answers, their consequences for schema, API, and the CMS, and a recommendation with spec
citations; draft any spec-change-request text. Implement nothing and edit no file other than
QUESTIONS.md.
```
