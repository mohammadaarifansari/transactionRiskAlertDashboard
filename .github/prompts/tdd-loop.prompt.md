---
description: "Run the full TDD loop for a single unit of work: write failing tests first, confirm they fail, implement to pass, then verify. Use for any Angular service, Java service, calculator, or mapper."
name: "TDD Loop"
model: "Claude Sonnet 4.6"
agent: "agent"
tools: [read, search, edit]
argument-hint: "Name the unit under test (service, calculator, mapper) and describe the behaviour to implement."
---

You are working on the transaction risk dashboard using the
[testing instructions](../instructions/testing.instructions.md),
[base constraints](../instructions/base.instructions.md), and
[Blueprint.md](../../Blueprint.md).

## Task

Implement a single unit of behaviour using strict TDD:
**red → green → verify**.

Never write implementation code before the test file exists and is confirmed to define
the expected behaviour.

## Inputs Required

1. What is the unit under test? (class name, file path, or description)
2. What behaviour should be implemented? (one focused capability per loop)
3. Which domain does it belong to? (frontend Angular service / Java backend service)

## Step 1 — Read Before Writing

- Read the existing file if it already exists.
- Read [Blueprint.md](../../Blueprint.md) for the relevant data model or logic spec.
- Read any sibling service or calculator for naming and structural conventions.
- Confirm the threshold values and weights from the base constraints before writing
  any scoring or tier-mapping logic.

## Step 2 — Write Tests First (RED)

Create or update the test file only. Do not touch the implementation yet.

For Angular (`.spec.ts`):
- Use Jasmine `describe` / `it` blocks with descriptive names.
- Name pattern: `'should <expected behaviour> when <condition>'`.
- Cover: happy path, boundary values, empty input, null/undefined input, error cases.
- For scoring: always include tests at exact tier threshold boundaries
  (0.39 / 0.40 and 0.69 / 0.70).
- For search: cover exact match, partial match, no match, and blank input.
- For risk rendering: test that contributing signals are present in output, not only the total score.
- Mock only true external boundaries (HTTP, storage). Keep pure logic tests free of mocks.
- Tests must be independent and deterministic — no shared mutable state between cases.

For Java (`*Test.java` / `*IT.java`):
- Use JUnit 5 `@Test` with `@DisplayName` describing behaviour.
- Use AssertJ `assertThat` for expressive assertions with meaningful failure messages.
- Cover: happy path, boundary, invalid input, empty collection, null guard.
- For scoring: use `@ParameterizedTest` for threshold boundary cases (0.39/0.40 and 0.69/0.70).
- For risk scoring: verify `explanation` and `weightedContribution` fields, not only `totalRiskScore`.
- Add parity tests confirming Java tier and score outputs match approved frontend reference fixtures.
- Use `*IT.java` naming for any test that requires Spring context or infrastructure.
- Keep unit tests pure — no Spring context unless testing a controller or integration boundary.

Confirm the test file is complete before moving to Step 3.

## Step 3 — Confirm Tests Fail (RED check)

State explicitly which tests are expected to fail and why:

- List each test name.
- State what error or assertion failure is expected (method not found, assertion mismatch, etc.).
- Do not proceed until you have confirmed the tests are red for the right reason.

If you cannot execute the tests directly, reason through each test against the current source
and confirm it would fail.

## Step 4 — Implement To Pass (GREEN)

Write the minimum implementation to make all tests pass.

Rules:
- Do not add logic not covered by a test.
- For Angular services: keep pure functions free of Angular dependencies where possible.
- For Java services: keep scoring logic in plain methods with no framework coupling.
- Centralise thresholds and weights — never hardcode them inline.
- Guard all inputs: null check, empty check, range clamp where appropriate.

## Step 5 — Verify All Tests Pass

Confirm each test written in Step 2 now passes:

- List each test and its result (PASS).
- If any test still fails, return to Step 4 without changing the tests.
- Do not mark the loop complete until all tests are green.
- Confirm assertions target business behaviour, not implementation details.
- Confirm risk scoring tests verify explainability fields (explanation, contribution) not only the total score.
- Confirm tests are deterministic: same input always produces same result.
- Confirm tests are suitable for CI execution with no environment-specific dependencies.

## Step 6 — Refactor If Needed

Only after all tests pass:
- Remove duplication.
- Improve naming and readability.
- Ensure no magic numbers remain.
- Re-confirm tests still pass after any refactor.

## Output

Summarise:
- Unit implemented.
- Tests written (count and names).
- All tests passing: yes / no.
- Any edge cases deferred and why.
- Follow-up items if any behaviour was out of scope for this loop.
