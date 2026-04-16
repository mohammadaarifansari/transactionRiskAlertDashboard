---
name: run-tests
description: 'Run the project test suite correctly for Angular frontend and Java backend. Use when verifying tests pass after implementation, checking coverage, or confirming TDD requirements are met. Outputs structured pass/fail results including test counts and failing test names.'
argument-hint: 'Specify scope: "frontend", "backend", or "all" (default). Optionally add "coverage" to include a coverage report.'
user-invocable: true
---

# Run Tests

## Purpose

Execute automated tests for the transaction risk dashboard and return structured, parseable
pass/fail output so agents and humans can act on results without ambiguity.

## When To Use

- After implementing a feature to verify nothing is broken.
- Before marking a backlog item as done to confirm acceptance criteria are met.
- When TDD requirements must be verified end-to-end.
- When another agent needs to validate test health before handoff.

## Procedure

1. Determine scope from the argument: `frontend`, `backend`, or `all`.
2. Run [./scripts/run-tests.sh](./scripts/run-tests.sh) with the appropriate scope flag.
3. Read the structured output from stdout.
4. Report results using the Output Format below.
5. If any tests fail, list each failing test name and its file path.
6. Exit code from the script must be checked: `0` = all pass, `1` = failures present.

## Commands By Scope

### Frontend (Angular)

```bash
bash .github/skills/run-tests/scripts/run-tests.sh frontend
```

Runs `ng test --watch=false --browsers=ChromeHeadless` inside the Angular project root.
Requires Node.js and Angular CLI to be available.

### Backend (Java / Maven)

```bash
bash .github/skills/run-tests/scripts/run-tests.sh backend
```

Runs `mvn test -q` inside the backend project root.
Requires JDK 21 and Maven to be available.

### All

```bash
bash .github/skills/run-tests/scripts/run-tests.sh all
```

Runs frontend then backend sequentially and aggregates results.

### With Coverage

Append `coverage` as second argument to either scope:

```bash
bash .github/skills/run-tests/scripts/run-tests.sh frontend coverage
bash .github/skills/run-tests/scripts/run-tests.sh backend coverage
```

## Output Format

The script and the agent reporting results must both use this structure:

```
=== TEST RESULTS ===
Scope    : frontend | backend | all
Status   : PASSED | FAILED
Total    : <number>
Passed   : <number>
Failed   : <number>
Skipped  : <number>

--- Failing Tests ---
<test name> (<file path>)
...
(none if all pass)

--- Coverage Summary (if requested) ---
Statements : <percent>%
Branches   : <percent>%
Functions  : <percent>%
Lines      : <percent>%

Exit Code  : 0 | 1
====================
```

## Agent Responsibilities

- Always report exit code alongside results.
- Never suppress failing test names.
- Treat exit code `1` as a blocker; do not mark work as done.
- If the environment cannot run tests (missing CLI, missing JDK), report the missing
  dependency clearly instead of silently skipping.
