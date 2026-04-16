---
description: "Use when generating or modifying Java backend code, APIs, service logic, validation, DTOs, and production-ready backend behavior for the transaction risk dashboard. Covers Java coding standards and architecture."
name: "Backend Java Standards"
applyTo:
  - "**/*.java"
  - "**/pom.xml"
  - "**/src/main/resources/**/*.properties"
  - "**/src/main/resources/**/*.yaml"
  - "**/src/main/resources/**/*.yml"
---
# Backend Java Standards

## Scope

- Apply these rules when creating or editing Java backend code.
- Default to production-ready service and API design.
- Keep modules cohesive and responsibilities clear.
- DO NOT read, review, update, or modify any frontend files (Angular components, templates, styles, TypeScript UI code, or anything under a frontend/src directory) without explicit user permission. Always ask the user before accessing frontend files.

## Project Expectations

- Build backend capabilities for the transaction risk alert dashboard domain.
- Keep business logic deterministic and explainable.
- Treat the project blueprint in [Blueprint.md](../../Blueprint.md) as the source of truth for entities, API surface, and constraints.
- Avoid introducing external dependencies or integrations unless explicitly requested.
- Keep initial implementations compatible with local/mock data contracts.

## Java Standards

- Target Java 21.
- Follow standard Java formatting conventions.
- Use 4 spaces for indentation.
- Use PascalCase for classes, camelCase for methods and fields, and UPPER_SNAKE_CASE for constants.
- Keep one public class per file.
- Prefer immutable value objects where practical.
- Use records for DTOs when appropriate.
- Use enums for bounded domains such as risk tier and fraud signal type.
- Keep controllers thin and place business logic in services.
- Validate inbound data at the boundary.
- Use explicit exception handling and meaningful error responses.
- Avoid field injection.
- Prefer constructor injection.

## Architecture Rules

- Separate controller, service, and repository responsibilities.
- Keep scoring and aggregation logic in pure, testable service methods.
- Centralize thresholds and signal weights in configuration.
- Keep API contracts explicit and version-ready.
- Use decimal-safe monetary types.
- Use ISO 8601 timestamps for temporal data.

## API And Data Rules

- Represent risk tiers as GREEN, YELLOW, and RED only.
- Use configured threshold bands: GREEN 0.00-0.39, YELLOW 0.40-0.69, RED 0.70-1.00.
- Ensure every computed risk decision is explainable through contributing signals.
- Maintain stable response shapes so frontend integrations remain predictable.
- Use clear domain models and DTO mappings.

## Production Readiness Rules

- No stubbed TODO logic in delivered code.
- Sanitize and validate all external input.
- Guard against null and malformed data.
- Avoid magic numbers by extracting constants and config.
- Use structured and intentional logging.
- Ensure code is suitable for CI linting, tests, and packaging.
- Favor clarity over cleverness.
