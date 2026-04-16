---
description: "Use when generating or modifying Angular 21 frontend code, UI components, templates, styles, or client-side data handling for the transaction risk dashboard. Covers architecture, accessibility, and production frontend standards."
name: "Frontend Angular Standards"
applyTo:
  - "**/*.component.ts"
  - "**/*.service.ts"
  - "**/*.directive.ts"
  - "**/*.pipe.ts"
  - "**/*.guard.ts"
  - "**/*.resolver.ts"
  - "**/*.config.ts"
  - "**/*.model.ts"
  - "**/*.types.ts"
  - "**/*.interface.ts"
  - "**/*.util.ts"
  - "**/main.ts"
  - "**/app.config.ts"
  - "**/*.html"
  - "**/*.scss"
  - "**/*.css"
---
# Frontend Angular Standards

## Scope

- Apply these rules when creating or editing Angular frontend code.
- Default to maintainable, production-ready implementation.
- Prefer small, cohesive modules with clear responsibilities.

## Project Expectations

- Build the application as a transaction risk alert dashboard for fraud operations users.
- Keep client-side behavior deterministic and explainable.
- Treat the project blueprint in [Blueprint.md](../../Blueprint.md) as the source of truth for features and interfaces.
- Do not add external APIs or third-party services unless explicitly requested.
- Use local mock data as the default source in early phases.

## Architecture Rules

- Keep frontend code separated into data access, domain logic, and presentation concerns.
- Place fraud scoring, thresholds, and aggregation logic in services or utility modules, not components.
- Keep components thin and focused on rendering and interaction.
- Centralize risk weights and thresholds in config objects.
- Use typed contracts for all request, response, and view model data.

## Angular Standards

- Use Angular 21 with standalone components.
- Prefer Angular Signals for local state and derived state.
- Use reactive forms for search and filtering.
- Use inject() where it improves readability and matches existing style.
- Keep feature code grouped by domain areas such as account-search, risk-summary, timeline, and shared.
- Use OnPush-compatible patterns and avoid unnecessary mutable shared state.
- Use RxJS where it adds clear value for async and stream composition.
- Use typed interfaces for all JSON structures.
- Keep templates declarative and avoid business logic in HTML.
- Handle loading, empty, and error states explicitly.
- Ensure keyboard accessibility, semantic markup, and sufficient color contrast.

## API And Contract Rules

- Keep frontend contracts stable and explicit.
- Use ISO 8601 timestamps.
- Represent risk tiers as GREEN, YELLOW, and RED only.
- Use configured threshold bands: GREEN 0.00-0.39, YELLOW 0.40-0.69, RED 0.70-1.00.
- Ensure each displayed risk outcome is traceable to contributing signals.
- When using static JSON data, keep response shapes aligned to planned production APIs.

## Production Readiness Rules

- No stubbed TODO logic in delivered code.
- Validate and sanitize data before display.
- Guard against null, missing, malformed, and empty states.
- Avoid magic numbers by centralizing constants.
- Return actionable and user-friendly error states.
- Ensure generated UI is responsive and accessible.
- Favor clarity over cleverness.
