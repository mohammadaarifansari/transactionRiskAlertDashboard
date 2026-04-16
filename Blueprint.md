# Transaction Risk Alert Dashboard Blueprint

## Purpose

This application helps fraud operations teams assess account risk at a glance using recent transaction activity. The system must identify whether an account should be treated as `GREEN`, `YELLOW`, or `RED`, explain the drivers behind that decision, and show how transaction behavior changed over the last 24 hours.

This document is the single source of truth for the project's scope, user scenarios, feature set, data model, interface contract, and constraints.

## Users And Scenarios

### Primary Users

- Fraud operations analysts reviewing suspicious account behavior.
- Risk investigators validating whether a customer requires manual review or escalation.
- Operations leads who need a quick, explainable account risk snapshot.

### Core User Scenarios

1. An analyst searches for an account by account ID or customer name and expects an immediate risk summary.
2. An investigator reviews the current fraud signals and needs plain-language explanations of the strongest risk contributors.
3. A reviewer inspects the last 24 hours of activity to see changes in amount patterns, transaction frequency, and risk escalation.
4. A user encounters an unknown account or an account with no recent activity and must receive a clear, graceful response.

## Feature List

### 1. Account Search And Retrieval

- Search by account ID.
- Search by customer name.
- Load account and transaction data from `data/transactions.json`.
- Support exact and user-friendly partial matching where feasible.
- Show a clear not-found state for missing accounts.
- Show a clear empty-history state when the account exists but has no transactions.

### 2. Risk Assessment Summary

- Calculate the current account risk tier as `GREEN`, `YELLOW`, or `RED`.
- Display weighted fraud indicators:
- Velocity anomaly.
- Geo anomaly.
- Unusual merchant category.
- High-value spike.
- Show top contributing risk factors in plain language.
- Display account metadata.
- Display the timestamp of the most recent assessment.
- Show a recommended operational action such as monitor, review, or block and escalate.

### 3. 24-Hour Transaction Timeline

- Show transactions from the most recent rolling 24-hour window.
- Visualize transaction amount changes over time.
- Visualize risk score change over time.
- Show transaction count per hour as a bar chart or sparkline.
- Highlight elevated-suspicion windows.

### 4. Explainability And Auditability

- Show how each fraud signal contributes to the total risk score.
- Keep weights and thresholds configurable.
- Ensure risk decisions are deterministic and reproducible for the same data set.

### 5. Resilience And UX States

- Handle malformed records defensively.
- Handle empty search input gracefully.
- Provide loading, no-result, and no-data states.
- Preserve readability and usability on desktop and tablet layouts.

## Data Model

### Account

Represents a customer account shown in search results and risk summaries.

| Field | Type | Required | Notes |
|---|---|---|---|
| accountId | string | Yes | Unique account identifier |
| customerName | string | Yes | Customer full name |
| accountStatus | string | No | Example: ACTIVE, BLOCKED |
| region | string | No | Customer or account region |
| segment | string | No | Business or customer segment |
| createdAt | string | No | ISO 8601 timestamp |

### Transaction

Represents an individual transaction used for aggregation and signal calculation.

| Field | Type | Required | Notes |
|---|---|---|---|
| transactionId | string | Yes | Unique transaction identifier |
| accountId | string | Yes | Foreign key to Account |
| timestamp | string | Yes | ISO 8601 timestamp |
| amount | number | Yes | Monetary amount |
| currency | string | Yes | Example: USD |
| merchantName | string | No | Merchant display name |
| merchantCategory | string | Yes | Used for unusual category checks |
| merchantCountry | string | No | Used for geo anomaly checks |
| channel | string | No | Example: CARD, TRANSFER, ONLINE |
| status | string | No | Example: APPROVED, DECLINED |

### FraudSignalAssessment

Represents the calculated outcome for each fraud signal.

| Field | Type | Required | Notes |
|---|---|---|---|
| signalType | string | Yes | One of VELOCITY, GEO, MCC, HIGH_VALUE |
| rawValue | number | Yes | Measured signal value |
| normalizedScore | number | Yes | Score from 0.0 to 1.0 |
| weight | number | Yes | Configured contribution weight |
| weightedContribution | number | Yes | normalizedScore x weight |
| explanation | string | Yes | Plain-language summary |

### RiskAssessment

Represents the account-level decision shown to users.

| Field | Type | Required | Notes |
|---|---|---|---|
| accountId | string | Yes | Related account |
| assessmentTimestamp | string | Yes | ISO 8601 timestamp |
| totalRiskScore | number | Yes | Value from 0.0 to 1.0 |
| riskTier | string | Yes | GREEN, YELLOW, or RED |
| contributingSignals | FraudSignalAssessment[] | Yes | Ordered by impact |
| recommendedAction | string | Yes | Monitor, review, or block/escalate |

### HourlyRiskWindow

Represents an aggregated hourly bucket in the 24-hour timeline.

| Field | Type | Required | Notes |
|---|---|---|---|
| hourStart | string | Yes | ISO 8601 timestamp |
| transactionCount | number | Yes | Count within the hour |
| totalAmount | number | Yes | Sum of amounts within the hour |
| averageAmount | number | No | Optional derived metric |
| windowRiskScore | number | Yes | Aggregated hourly risk |
| elevatedSuspicion | boolean | Yes | True when the hour crosses threshold |

### Relationships

- One Account has many Transactions.
- One Account has one latest RiskAssessment.
- One RiskAssessment has many FraudSignalAssessments.
- One Account has up to 24 HourlyRiskWindows for the last rolling day.

## Risk Logic Baseline

The application should calculate risk using four weighted fraud signals.

| Signal | Description | Suggested Weight |
|---|---|---|
| Velocity anomaly | Unusual transaction frequency in a short period | 0.35 |
| Geo anomaly | Unusual country or location pattern | 0.25 |
| Unusual merchant category | Unexpected merchant category behavior | 0.15 |
| High-value spike | Amount significantly above normal pattern | 0.25 |

### Tier Thresholds

- `GREEN`: 0.00 to 0.39
- `YELLOW`: 0.40 to 0.69
- `RED`: 0.70 to 1.00

Thresholds and weights should be configurable and not hard-coded directly into UI components.

## API Surface

The first delivery may be frontend-only with static JSON, but all data access should follow a stable interface so a Java backend can replace the mock source later without breaking the UI contract.

### Frontend Data Access Interface

- `searchAccounts(query: string): Account[]`
- `getAccountById(accountId: string): Account | null`
- `getTransactionsByAccountId(accountId: string): Transaction[]`
- `getLatestRiskAssessment(accountId: string): RiskAssessment`
- `getHourlyRiskWindows(accountId: string, hours: number): HourlyRiskWindow[]`

### Planned REST API Contract

If a backend is introduced, keep the contract aligned to these endpoints:

- `GET /api/accounts?query={value}`
- `GET /api/accounts/{accountId}`
- `GET /api/accounts/{accountId}/transactions?window=24h`
- `GET /api/accounts/{accountId}/risk-assessment`
- `GET /api/accounts/{accountId}/risk-windows?window=24h`

### Response Principles

- All timestamps use ISO 8601.
- Monetary fields use decimal-safe representation in backend contracts.
- Errors return clear, user-safe messages.
- Response shapes should be explicit and typed.

## Architectural Layers

### Data Layer

- Load and normalize static JSON data.
- Validate records before use.
- Provide query and mapping utilities.
- Remain replaceable by a future Java API without changing UI view models excessively.

### Logic Layer

- Calculate fraud signal scores.
- Calculate weighted total risk score.
- Map total score to risk tier.
- Generate plain-language explanations.
- Aggregate hourly activity for the 24-hour timeline.

### Presentation Layer

- Render search, risk summary, and timeline views.
- Show loading, empty, and error states.
- Present explainable, accessible risk decisions.
- Support desktop-first responsive layouts.

## Constraints

- No external APIs are required for the initial version.
- Initial data source is local mock data stored in `data/transactions.json`.
- The application must work without a database in the first phase.
- Risk calculations must be deterministic.
- The UI must handle missing accounts and empty histories gracefully.
- The application must remain explainable; every tier must map back to signal contributions.
- Business logic should not be embedded in presentation templates.
- Unit tests are required for non-trivial logic.
- Code should be production ready, not prototype-only.

## Non-Functional Requirements

- Fast local performance on mock data.
- Readable and accessible UI.
- Maintainable structure with strong typing and clear separation of concerns.
- Testable services and deterministic outputs.
- Formatting and naming should follow Angular and Java norms.

## Delivery Guidance

- Start with Angular 21 and local JSON data.
- Keep service interfaces stable so a Java 21 Spring Boot backend can be added later.
- Build charts and timelines on top of aggregated hourly risk windows rather than raw rendering alone.
- Treat this blueprint as the reference document for all future coding agents and implementation work.