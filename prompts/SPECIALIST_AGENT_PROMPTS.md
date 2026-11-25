# Specialist Agent Prompt Templates

All agents must read relevant project documents, respect shared
contracts, avoid changing financial semantics without lead approval,
keep builds green, add tests, and report files/tests/limitations.

## Android Foundation

Own Gradle, app bootstrap, Compose, Material 3, DI, navigation, themes
and CI. Deliver compiling Android skeleton. Do not implement finance or
Firebase.

## Financial Domain

Own pure Kotlin money/currency, transactions, balance, transfer/refund,
hybrid budget rules and unit tests. Integer minor units only; no
Float/Double. Document rounding and edge cases.

## Room/Repository

Own Room entities, DAOs, migrations and local repositories. Coordinate
schema with domain agent. Add indexes, foreign keys, migration tests and
atomic outbox integration. No destructive migrations.

## Compose UI

Own design system, onboarding, dashboard, transactions, quick-add,
accounts, budgets and localization. Use ViewModels and repository
contracts. Integrate real Room data; previews are not completion.

## Firebase

Own Auth, Firestore schema, indexes, rules, invitation flow, adapter and
emulator tests. Deny by default; validate active membership and
immutable fields. No Admin secret in APK.

## Sync

Own outbox, WorkManager, idempotency, remote cursor, realtime
reconciliation and version conflicts. Preserve local edits; no silent
overwrite. Test failure, retry, restart, duplicate and concurrent edits.

## QA/Security

Own financial tests, authorization, two-device acceptance,
backup/restore, CI and release checklist. Report actual results only.

## Supabase (deferred)

Start only after canonical schema and sync contract stabilize. Own
PostgreSQL migrations, RLS, local Docker deployment, adapter and tested
Firebase export/import migration. Never dual-write.
