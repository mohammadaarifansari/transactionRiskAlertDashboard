---
description: "Create an Angular 21 UI component connected to a data service. Use when building account search, risk summary card, timeline, or explainability panel components for the transaction risk dashboard."
name: "Create UI Component"
model: "Claude Sonnet 4.6"
agent: "agent"
tools: [read, search, edit]
argument-hint: "Name the component to create and the data service it should consume."
---

You are working on the transaction risk dashboard frontend using the
[frontend instructions](../instructions/frontend.instructions.md) and
[base constraints](../instructions/base.instructions.md).
The project blueprint is at [Blueprint.md](../../Blueprint.md).

## Task

Create a production-ready Angular 21 standalone component connected to an existing data service.

## Inputs Required

Answer these before proceeding (or infer from context):

1. What is the component name? (e.g. `RiskSummaryCardComponent`)
2. What feature domain does it belong to? (account-search, risk-summary, timeline, shared)
3. Which service(s) does it consume?
4. What data does it display and what user interactions does it support?

## Step 1 — Read Existing Context

Before writing any code:

- Read the target service file to understand its public API and return types.
- Read [Blueprint.md](../../Blueprint.md) for the relevant feature section.
- Search for any existing component in the same domain to match naming and file layout.

## Step 2 — Create The Component

Generate four files:

- `<name>.component.ts` — standalone component class
- `<name>.component.html` — declarative template
- `<name>.component.scss` — scoped styles
- `<name>.component.spec.ts` — unit tests (written first, see Step 4)

Rules for the component class:
- Use `standalone: true` and `changeDetection: ChangeDetectionStrategy.OnPush`.
- Use `inject()` for all service dependencies — do not use constructor injection.
- Use Angular Signals (`signal`, `computed`) for local and derived synchronous state.
- Use RxJS only where it adds clear value for async streams or event composition;
  do not introduce observable complexity where a signal is sufficient.
- Use `ReactiveFormsModule` and `FormControl` / `FormGroup` for any search or filter inputs.
- Expose only what the template needs; keep scoring and aggregation logic in services.
- Declare explicit `@Input()` types — no implicit `any`.
- Format ISO 8601 timestamps using `DatePipe` or a shared utility — never display raw strings.
- Ensure each displayed risk outcome includes its contributing signals so it is traceable.

Rules for the template:
- Use `@if`, `@for`, `@switch` control flow blocks.
- Render explicit loading, empty, and error states for every async data path.
- Use semantic HTML elements (`<section>`, `<article>`, `<header>`, `<ul>`, etc.).
- Add `aria-label` or `aria-live` where dynamic content needs screen reader context.
- Do not embed calculations or conditional business logic in template expressions.

Rules for styles:
- Scope all styles to the component.
- Use CSS custom properties for risk tier colours (GREEN, YELLOW, RED) — do not hardcode hex values.
- Ensure sufficient colour contrast for tier badges and status indicators.

## Step 3 — Connect To The Service

- Call the service in `ngOnInit` or via a signal-driven effect — not in the constructor.
- Map service output to view models if the raw type contains fields the template should not see.
- Handle `null` and empty returns gracefully with fallback UI.

## Step 4 — Write Unit Tests First (TDD)

Per the [testing instructions](../instructions/testing.instructions.md), write the spec file
before considering the component done. Mock only true external boundaries (services);
keep all rendering and state tests isolated from infrastructure.

Required test cases:
- Component creates successfully.
- Loading state renders while data is pending.
- Empty state renders when service returns empty array or null.
- Error state renders when service throws.
- Correct data renders for a known fixture input.
- Risk tier badge shows correct text label AND correct CSS class for GREEN, YELLOW, and RED.
- Contributing signals are displayed and traceable for each rendered risk outcome.
- Timestamp is formatted correctly and not displayed as a raw ISO string.
- Reactive form input (if present) triggers search correctly and handles empty input.
- Keyboard interaction works for all interactive elements.
- Assertions must validate displayed business behaviour, not component internals.

## Step 5 — Verify

Confirm:
- [ ] Component compiles with no TypeScript errors.
- [ ] `inject()` used for all service dependencies.
- [ ] Template has no implicit `any` bindings.
- [ ] All three states (loading, empty, error) are handled.
- [ ] Reactive forms used for any search or filter input.
- [ ] Risk outcomes include contributing signals — every displayed tier is traceable.
- [ ] Timestamps displayed via DatePipe or equivalent — not as raw ISO strings.
- [ ] Accessibility: semantic markup and aria attributes present.
- [ ] Color contrast sufficient for tier badges — CSS custom properties used, no hardcoded hex.
- [ ] Unit tests pass; assertions target business behaviour.
- [ ] No business logic in the template.

## Output

Summarise:
- Files created.
- States handled and how.
- Tests added and their coverage areas.
- Any gaps or follow-up items.
