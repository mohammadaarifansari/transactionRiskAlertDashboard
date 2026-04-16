# Developer TODO

This plan breaks delivery into small, agent-friendly tasks.

Rules applied to every task:
- Maximum scope: each task should touch 5 files or fewer.
- Done means testable acceptance criteria are met.
- TDD is mandatory: tests must be written first and passing before task completion.

## Phase 0: Workspace Prerequisite

> **BLOCKER**: Phase 0 must be complete before any Phase 1 task is started. No application source files can be compiled, tested, or linted until the Angular workspace exists.

| Task ID | Domain Tag | Task | File Budget | Acceptance Criterion | TDD Requirement |
|---|---|---|---|---|---|
| P0-T1 | frontend | Scaffold Angular 21 workspace with strict TypeScript, Angular ESLint, and Karma/Jasmine test runner. Create data/ directory placeholder at repo root. | <=5 files edited by hand (CLI generates remainder) | `ng build` completes with zero errors; `ng test --watch=false` reports 0 failures on the default app spec; `ng lint` reports 0 errors; `tsconfig.json` has `"strict": true`; `data/` directory exists at repo root. | The Angular CLI default app spec (app.component.spec.ts) must execute and pass as the minimum viable test gate. If the spec is missing, add a single smoke test asserting AppComponent is created before marking done. |

## Phase 1: Foundation And Data Contracts

| Task ID | Domain Tag | Task | File Budget | Acceptance Criterion | TDD Requirement |
|---|---|---|---|---|---|
| P1-T1 | data-layer | Define TypeScript domain models for Account, Transaction, FraudSignalAssessment, RiskAssessment, and HourlyRiskWindow. | <=5 files | Models compile with strict typing and match Blueprint entity fields exactly. | Add model contract tests that validate required fields and enum values for risk tiers and signal types. |
| P1-T2 | data-layer | Add JSON loader and parser for data/transactions.json with validation guards for malformed records. | <=5 files | Loader returns normalized account and transaction collections; malformed records are rejected with safe errors. | Write parser tests first for valid payload, missing fields, malformed timestamps, and empty arrays. |
| P1-T3 | data-layer | Implement search utilities for accountId and customerName (exact + partial match). | <=5 files | Search returns deterministic, case-insensitive results and no duplicates for a query. | Create search tests first for accountId hit, customer name partial hit, no result, and blank query behavior. |

## Phase 2: Risk Logic Core

| Task ID | Domain Tag | Task | File Budget | Acceptance Criterion | TDD Requirement |
|---|---|---|---|---|---|
| P2-T1 | logic-layer | Implement signal calculators: velocity anomaly, geo anomaly, unusual merchant category, and high-value spike. | <=5 files | Each signal returns normalized score from 0.0 to 1.0 and explanation text. | Add unit tests first for each signal covering normal, boundary, and extreme transaction patterns. |
| P2-T2 | logic-layer | Implement weighted scoring service using configurable weights and compute totalRiskScore. | <=5 files | Total score equals weighted sum of all signal outputs and is stable for the same input dataset. | Write tests first for weight application, floating-point rounding policy, and deterministic repeat runs. |
| P2-T3 | logic-layer | Implement risk tier mapper and recommended action mapper (GREEN, YELLOW, RED). | <=5 files | Tier mapping follows configured thresholds and action text is correct for all tiers. | Create boundary tests first at 0.39/0.40 and 0.69/0.70 to verify exact tier transitions. |
| P2-T4 | logic-layer | Implement contribution ranking and plain-language top factor summary generation. | <=5 files | Top factors are sorted by weighted contribution descending and include readable explanations. | Add tests first for sort order, tie-breaking behavior, and human-readable output strings. |

## Phase 3: 24-Hour Aggregation

| Task ID | Domain Tag | Task | File Budget | Acceptance Criterion | TDD Requirement |
|---|---|---|---|---|---|
| P3-T1 | logic-layer | Build rolling 24-hour transaction filter and hourly bucket aggregator. | <=5 files | Returns exactly up to 24 ordered hourly windows with count and amount totals. | Write tests first for timezone-safe window slicing, empty windows, and bucket ordering. |
| P3-T2 | logic-layer | Compute per-hour windowRiskScore and elevatedSuspicion boolean for timeline highlighting. | <=5 files | Suspicion flag activates only when configured threshold is crossed; score remains 0.0 to 1.0. | Create tests first for below-threshold, at-threshold, and above-threshold hourly windows. |

## Phase 4: Frontend Search And Risk Summary

| Task ID | Domain Tag | Task | File Budget | Acceptance Criterion | TDD Requirement |
|---|---|---|---|---|---|
| P4-T1 | frontend | Implement search form UI with account ID or customer name input and submit behavior. | <=5 files | User can search by both modes and trigger result loading from local data service. | Write component tests first for form validation, submit events, and keyboard interaction. |
| P4-T2 | frontend | Implement account risk summary card with tier badge, score, metadata, and assessment timestamp. | <=5 files | Summary renders correct tier color and metadata for a selected account. | Add tests first asserting tier label, tier color class, metadata rendering, and timestamp formatting. |
| P4-T3 | frontend | Implement top contributing factors panel with plain-language explanations. | <=5 files | Top 2 to 3 factors display in ranked order and match computed contributions. | Write tests first for ranking display, explanation rendering, and empty factor fallback state. |
| P4-T4 | frontend | Implement empty, no-result, and malformed-data error states in UI. | <=5 files | UI shows clear and accessible fallback states without runtime errors. | Add tests first for no account found, no transactions, and loader error scenarios. |

## Phase 5: Timeline Visuals

| Task ID | Domain Tag | Task | File Budget | Acceptance Criterion | TDD Requirement |
|---|---|---|---|---|---|
| P5-T1 | frontend | Add hourly transaction count chart (bar or sparkline) using aggregated windows. | <=5 files | Chart reflects hourly counts accurately and updates when account changes. | Write chart adapter tests first to verify labels and data arrays map correctly from hourly windows. |
| P5-T2 | frontend | Add amount trend and risk trend timeline charts for last 24 hours. | <=5 files | Both trends render with ordered hourly points and remain stable for empty windows. | Add tests first for data transforms, ordering, and zero-data placeholders. |
| P5-T3 | frontend | Add visual highlight for elevated suspicion windows on timeline. | <=5 files | Elevated windows are visually distinct and correspond to elevatedSuspicion=true buckets. | Write tests first for highlight class mapping and non-highlight behavior for normal windows. |

## Phase 6: API Layer Preparation (Mock Interface First)

| Task ID | Domain Tag | Task | File Budget | Acceptance Criterion | TDD Requirement |
|---|---|---|---|---|---|
| P6-T1 | api-layer | Introduce frontend data-access interface matching Blueprint planned API methods. | <=5 files | UI consumes interface methods only and no longer depends on raw JSON shape directly. | Add tests first for interface adapter behavior and method contracts returning typed results. |
| P6-T2 | api-layer | Add mock endpoint adapter implementation aligned to planned REST response contracts. | <=5 files | Mock adapter returns response objects consistent with future backend contract fields. | Write tests first asserting response schema compatibility and error envelope shape. |

## Phase 7: Backend Java Increment (Optional For Phase 2 Delivery)

| Task ID | Domain Tag | Task | File Budget | Acceptance Criterion | TDD Requirement |
|---|---|---|---|---|---|
| P7-T1 | backend-java | Create Java domain DTOs and enums mirroring Blueprint entities and tiers. | <=5 files | DTOs compile, use Java naming conventions, and represent required fields exactly. | Write JUnit tests first for enum constraints and serialization/deserialization compatibility. |
| P7-T2 | backend-java | Implement risk scoring service in Java using same weight and threshold config. | <=5 files | Java service produces same score and tier outputs as frontend reference cases. | Add JUnit tests first for signal calculation, weighted scoring, and boundary threshold cases. |
| P7-T3 | backend-java | Implement read-only REST endpoints for account lookup, transactions, assessment, and windows. | <=5 files | Endpoints return typed JSON responses and meaningful errors for unknown accounts. | Create controller tests first for 200, 404, and validation error responses. |

## Phase 8: Quality Gates And Release Readiness

| Task ID | Domain Tag | Task | File Budget | Acceptance Criterion | TDD Requirement |
|---|---|---|---|---|---|
| P8-T1 | testing | Enforce minimum unit coverage thresholds for logic-layer and critical UI/service paths. | <=5 files | CI fails when coverage drops below agreed threshold for risk-critical modules. | Write failing CI configuration tests first, then set thresholds and make pipeline pass. |
| P8-T2 | testing | Add integration tests for full analyst flow: search -> risk summary -> timeline review. | <=5 files | End-to-end integration validates the primary fraud analyst scenario without flaky behavior. | Define scenario tests first with fixed mock data and deterministic expected outputs. |
| P8-T3 | frontend | Final accessibility and resilience pass for keyboard navigation, contrast, and state messaging. | <=5 files | Key views pass accessibility checks and all fallback states are usable and readable. | Add tests first for keyboard focus order, aria labels, and fallback message visibility. |

## Definition Of Done For Each Task

- Task code and tests are complete within the stated file budget.
- Acceptance criterion is verified by automated tests and local execution.
- No debug placeholders, TODO stubs, or unhandled edge paths remain.
- Code aligns with the instruction files in .github/instructions.
