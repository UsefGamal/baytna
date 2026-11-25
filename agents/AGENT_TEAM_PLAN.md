# Sub-Agent Team Plan

## Coordination

One lead/orchestrator owns architecture, task board, shared contracts,
integration, code review and final acceptance. Use separate
branches/worktrees. Do not allow concurrent edits to the same files.
Every agent reports changed files, interfaces, assumptions, tests,
limitations and integration steps.

## Agent roles

### 0 Lead Architect / Orchestrator

Owns architecture, ADRs, contracts, task assignment, integration, CI and
acceptance. Must personally review financial and security code.

### 1 Android Foundation

Owns Gradle, app module, Compose bootstrap, navigation, DI, Material3,
CI. Deliver compiling skeleton. No financial logic or cloud sync.

### 2 Financial Domain

Owns Money/Currency, domain models, account balance, transfer/refund,
budget calculations and unit tests. Pure Kotlin. No Float/Double.

### 3 Room and Repositories

Owns entities, DAOs, migrations, local repositories and persistence
tests. Coordinate schema with domain agent. No destructive migrations.

### 4 UI/UX

Owns design system, onboarding, dashboard, transactions, quick-add,
accounts, budgets, Arabic/RTL and themes. Use real repository interfaces
before acceptance; no fake-only completion.

### 5 Firebase

Owns Auth, Firestore schema/indexes, rules, invitation flow, emulator
tests and Firebase adapter. Deny by default; no Admin credentials in
APK.

### 6 Sync

Owns outbox, WorkManager, idempotency, cursor pull, realtime
reconciliation and conflicts. Coordinate contracts with Room and
Firebase agents. No silent financial overwrite.

### 7 QA/Security/Release

Owns tests, two-device acceptance, authorization tests, backup/restore
checks, CI release workflow and release documentation.

### 8 Supabase (deferred)

Only after schema and sync stabilize. Owns PostgreSQL migrations, RLS,
Docker deployment, adapter and migration tests. No dual-write.

## Parallel waves

Wave 1: lead + foundation + domain + Room schema draft. Wave 2: domain
completion + Room repos + UI + Firebase schema/rules. Wave 3: Firebase
adapter + sync + real UI integration + QA. Wave 4: two-device QA +
release. Supabase starts only after stable MVP.

## Rules

Read specs; do not invent requirements; do not change financial
semantics without approval; integer money only; Room is UI source; no
secrets; no dual-write; no silent conflict resolution; keep build green;
test all financial rules.
