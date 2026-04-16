---
name: project-metrics
description: 'Collect deterministic project state metrics before planning. Use when the PM agent needs source file counts by layer, test counts, and lint or build health signals without scanning files manually.'
argument-hint: 'No arguments required. Optionally pass "verbose" to include full per-directory file lists.'
user-invocable: true
---

# Project Metrics

## Purpose

Give the PM agent a deterministic snapshot of project state so planning decisions are based
on facts, not guesses. Must be run before selecting or elaborating backlog items.

## When To Use

- At the start of every PM planning session before touching developer_todo.md.
- When checking whether a phase has been started, is in progress, or is complete.
- When assessing how much test coverage infrastructure exists relative to source code.

## Procedure

1. Run [./scripts/collect-metrics.sh](./scripts/collect-metrics.sh) from the repo root.
2. Read the structured output block from stdout.
3. Use the counts and status signals as the planning baseline.
4. Do not manually count files or infer state from directory listings instead.

## Command

```bash
bash .github/skills/project-metrics/scripts/collect-metrics.sh
```

With verbose file lists:

```bash
bash .github/skills/project-metrics/scripts/collect-metrics.sh verbose
```

## Output Format

The script emits this exact structure. The PM agent must consume every section:

```
=== PROJECT METRICS ===
Timestamp  : <ISO 8601>
Repo Root  : <path>

--- Frontend Source (Angular) ---
Components          : <n>   (.component.ts)
Services            : <n>   (.service.ts)
Models / Interfaces : <n>   (.model.ts | .interface.ts | .types.ts)
Pipes / Directives  : <n>   (.pipe.ts | .directive.ts)
Guards / Resolvers  : <n>   (.guard.ts | .resolver.ts)
Config / Utils      : <n>   (.config.ts | .util.ts)
Templates           : <n>   (.html)
Styles              : <n>   (.scss | .css)
Frontend Spec Tests : <n>   (.spec.ts)
Total Frontend Src  : <n>

--- Backend Source (Java) ---
Controllers         : <n>   (*Controller.java)
Services            : <n>   (*Service.java | *ServiceImpl.java)
DTOs / Records      : <n>   (*Dto.java | *Request.java | *Response.java | *Record.java)
Repositories        : <n>   (*Repository.java)
Config / Util       : <n>   (*Config.java | *Util.java | *Helper.java)
Enums               : <n>   (*Enum.java | RiskTier.java | *Type.java)
Domain Models       : <n>   (remaining .java excluding tests and above)
Backend Unit Tests  : <n>   (*Test.java | *Tests.java)
Backend IT Tests    : <n>   (*IT.java | *ITCase.java)
Total Backend Src   : <n>

--- Data Layer ---
Mock Data Files     : <n>   (data/**/*.json)
Config Files        : <n>   (frontend src/**/*.config.ts | backend *Config.java)

--- Test Summary ---
Total Spec Tests    : <n>   (frontend .spec.ts)
Total Unit Tests    : <n>   (backend *Test.java + *Tests.java)
Total IT Tests      : <n>   (backend *IT.java + *ITCase.java)
Total Tests Overall : <n>

--- Lint / Build Signals ---
Frontend ESLint     : <OK | WARN | ERROR | NOT_CONFIGURED>
Frontend Build      : <OK | WARN | ERROR | NOT_CONFIGURED>
Backend Compile     : <OK | WARN | ERROR | NOT_CONFIGURED>
Backend Checkstyle  : <OK | WARN | ERROR | NOT_CONFIGURED>

--- Phase Readiness ---
Phase 1 (Data Models)     : <NOT_STARTED | IN_PROGRESS | PRESENT>
Phase 2 (Risk Logic)      : <NOT_STARTED | IN_PROGRESS | PRESENT>
Phase 3 (24h Aggregation) : <NOT_STARTED | IN_PROGRESS | PRESENT>
Phase 4 (Frontend UI)     : <NOT_STARTED | IN_PROGRESS | PRESENT>
Phase 5 (Timeline)        : <NOT_STARTED | IN_PROGRESS | PRESENT>
Phase 6 (API Layer)       : <NOT_STARTED | IN_PROGRESS | PRESENT>
Phase 7 (Java Backend)    : <NOT_STARTED | IN_PROGRESS | PRESENT>

======================
```

## PM Agent Rules

- Run this skill before every planning session. Do not skip it.
- Record the timestamp from the output so backlog items reference a known snapshot.
- If a layer shows 0 files, treat it as NOT_STARTED regardless of developer_todo.md status.
- If lint or build signals are ERROR, flag that as a blocker in the planning output.
