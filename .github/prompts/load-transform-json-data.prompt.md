---
description: "Load and transform data from a JSON source file into typed Angular models. Use when wiring up data/transactions.json to frontend services with validation and normalization."
name: "Load And Transform JSON Data"
model: "Claude Sonnet 4.6"
agent: "agent"
tools: [read, search, edit]
argument-hint: "Specify the JSON source file path and the target model or service name."
---

You are working on the transaction risk dashboard frontend using the
[frontend instructions](../instructions/frontend.instructions.md) and
[base constraints](../instructions/base.instructions.md).
The project blueprint is at [Blueprint.md](../../Blueprint.md).

## Task

Wire up a JSON data source to typed Angular models and a data-access service.

## Inputs Required

Answer these before proceeding (or infer from context if obvious):

1. Which JSON file is the source? (default: `data/transactions.json`)
2. Which model types need to be created or updated? (Account, Transaction, etc.)
3. Which service will own data loading and querying?

## Step 1 — Read The Data Source

Read the target JSON file to understand its actual shape before writing any code.

- Identify all fields present.
- Note which fields are required vs optional.
- Note field types (string, number, boolean, nested object, array).
- Flag any fields that deviate from the [Blueprint.md](../../Blueprint.md) data model.

## Step 2 — Define Or Update TypeScript Models

Create or update typed interfaces in the appropriate model file.

Rules:
- One interface per entity.
- Mark required fields without `?`, optional fields with `?`.
- Use `string` for ISO timestamps, `number` for amounts.
- Use a string literal union or enum for bounded fields (e.g. `'GREEN' | 'YELLOW' | 'RED'`).
- Place models in `src/app/shared/models/` or the appropriate domain folder.

## Step 3 — Implement The Data Loader

Create or update the Angular service that loads and validates the JSON.

Rules:
- Use `inject()` for all service dependencies — do not use constructor injection.
- Use `HttpClient` with `assets/` path or direct import for local JSON.
- Use RxJS (`Observable`, `map`, `catchError`) where async stream composition adds value;
  use Angular Signals for derived or cached synchronous state.
- Validate records on load: reject malformed entries, log a warning, continue with valid records.
- Expose typed query methods matching the Blueprint API surface:
  - `searchAccounts(query: string): Account[]` — case-insensitive, partial match on name and ID
  - `getAccountById(id: string): Account | null`
  - `getTransactionsByAccountId(id: string): Transaction[]`
- Return empty arrays not nulls for collections.
- Guard against null, undefined, and missing required fields before use.
- Place the service in its domain folder (e.g. `src/app/account-search/` or `src/app/shared/data-access/`).
- Keep the service OnPush-compatible: emit new references rather than mutating existing objects.
- Centralise field names, defaults, and any validation constants — no magic strings inline.

## Step 4 — Write Unit Tests First (TDD)

Per the [testing instructions](../instructions/testing.instructions.md), write tests before the
loader is considered done. Mock only true external boundaries (HttpClient); keep all
domain logic tests free of mocks.

Required test cases:
- Valid payload loads all records correctly.
- Malformed record is rejected without crashing.
- Missing required field returns a safe fallback.
- Empty JSON array returns an empty collection.
- `searchAccounts` returns results for exact account ID match.
- `searchAccounts` returns results for partial customer name match (case-insensitive).
- `searchAccounts` returns empty array for a query that matches nothing.
- `searchAccounts` returns empty array for a blank or whitespace-only query.
- `getAccountById` returns the correct account for a known ID.
- `getAccountById` returns `null` for an unknown ID.
- `getTransactionsByAccountId` returns empty array when account has no transactions.
- Tests must be deterministic and independent — no shared mutable state between cases.

## Step 5 — Verify

Confirm:
- [ ] Models match Blueprint entity fields exactly.
- [ ] Loader compiles with no TypeScript errors.
- [ ] `inject()` used for all service dependencies.
- [ ] Service is OnPush-compatible (no in-place mutation).
- [ ] Response shapes align to planned production API contracts.
- [ ] Unit tests pass and cover all search and edge-case scenarios above.
- [ ] Assertions validate business behaviour, not just implementation details.
- [ ] No business logic exists in the service beyond loading, validation, and querying.
- [ ] No magic strings or inline constants.

## Output

Summarise:
- Files created or changed.
- Any Blueprint deviations found in the JSON source and how they were handled.
- Tests added and their coverage areas.
