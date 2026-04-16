---
description: "Use when planning or implementing any part of the transaction risk dashboard to enforce cross-cutting constraints shared by frontend, backend, and testing work."
name: "Base Project Constraints"
---
# Base Project Constraints

## Source Of Truth

- Treat [Blueprint.md](../../Blueprint.md) as the single source of truth for scope, entities, interfaces, and constraints.

## Non-Negotiable Product Rules

- Risk tiers must be GREEN, YELLOW, and RED only.
- Tier thresholds must default to:
- GREEN: 0.00 to 0.39
- YELLOW: 0.40 to 0.69
- RED: 0.70 to 1.00
- Risk outcomes must be deterministic for the same input data.
- Every risk decision must be explainable through contributing fraud signals.

## Delivery Constraints

- Initial implementation should work without external APIs.
- Use local mock data as the default source during first-phase delivery.
- Keep data-access interfaces stable so a Java backend can replace mock data without breaking consumers.

## Engineering Constraints

- Keep business logic out of presentation templates.
- Centralize weights and thresholds in configuration, not scattered constants.
- Handle missing accounts, empty transaction history, and malformed records gracefully.
- Follow production-ready quality standards with automated tests for non-trivial logic.
