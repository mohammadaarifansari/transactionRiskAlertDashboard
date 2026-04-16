---
description: "Use when generating or modifying unit tests for Angular or Java, test plans, test data builders, and quality gates for production-ready delivery in the transaction risk dashboard project."
name: "Testing Standards"
applyTo:
  - "**/*.spec.ts"
  - "**/*.test.ts"
  - "**/*Test.java"
  - "**/*Tests.java"
  - "**/*IT.java"
  - "**/*ITCase.java"
---
# Testing Standards

## Scope

- Apply these rules when creating or updating automated tests.
- Prioritize deterministic, meaningful tests over broad but shallow coverage.
- Ensure test code is production-quality and maintainable.

## General Testing Rules

- Add unit tests for every non-trivial service, calculator, mapper, and utility.
- Cover happy path, boundary values, invalid input, and empty state behavior.
- Prefer descriptive test names that explain behavior and expected outcomes.
- Do not leave placeholder or skipped tests without explicit reason.
- Keep tests independent, deterministic, and fast.

## Angular Testing Rules

- Test risk calculation services thoroughly, including threshold boundaries.
- Test account search behavior for exact matches, partial matches, no matches, and empty input.
- Test components with conditional rendering for loading, error, and empty states.
- Test rendering of risk tiers, top factors, and timeline data states.
- Mock only true external boundaries and keep domain logic tests isolated.

## Java Testing Rules

- Use JUnit 5 for backend unit tests.
- Test service logic, validators, mappers, and risk scoring calculations.
- Verify tier mapping for GREEN, YELLOW, and RED boundary conditions.
- Mock external dependencies only when necessary.
- Keep domain logic tests pure and not dependent on infrastructure.
- Add parity tests so Java scoring and tier outcomes match approved frontend reference fixtures.

## Quality Gates

- Generated tests should compile and execute.
- Assertions should validate business behavior, not just implementation details.
- Risk scoring tests must verify explainability fields, not only score totals.
- Ensure tests are suitable for CI execution.
