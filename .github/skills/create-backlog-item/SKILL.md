---
name: create-backlog-item
description: 'Create consistently structured backlog items for planning. Use when elaborating tasks from developer_todo.md into implementation-ready items with scope, acceptance criteria, and TDD requirements.'
argument-hint: 'Provide item title, domain, dependencies, and planning context to produce a complete backlog item.'
user-invocable: true
---

# Create Backlog Item

## Purpose

Produce consistently structured, implementation-ready backlog items for this project.

## When To Use

- Breaking down or refining items from developer_todo.md.
- Preparing sprint-ready tasks for frontend, backend, testing, data, or API domains.
- Standardizing acceptance criteria and TDD requirements across items.

## Required Inputs

- Planning context and objective.
- Target domain tag.
- Source item or requirement reference.
- Dependencies and sequencing constraints.

## Output Structure

Use this exact structure for each backlog item:

1. Item ID: concise unique key
2. Title: short action-oriented name
3. Domain: one of data-layer, logic-layer, frontend, api-layer, backend-java, testing
4. Problem Statement: what outcome this item unlocks
5. Scope: explicit work included
6. Out Of Scope: explicit exclusions
7. File Budget: target <=5 files touched
8. Dependencies: prerequisite items or decisions
9. Acceptance Criteria:
- specific, testable completion statements
10. TDD Requirements:
- tests that must be created first
- boundary/edge cases that must be covered
11. Deliverables: expected artifacts
12. Risks And Assumptions: key planning caveats

## Quality Checks

- Item can be completed within one agent context window.
- Acceptance criteria are objective and verifiable.
- TDD requirements are concrete and behavior-focused.
- Scope does not overlap unrelated domains.
- Wording is implementation-ready and unambiguous.
