# Roadmap and Backlog

## Phase 0 --- Foundation

Create repo/Gradle project; configure Kotlin/Compose/Material3/version
catalog; DI/navigation; design tokens; localization; CI; ADRs. Exit: app
builds and launches in CI.

## Phase 1 --- Local finance engine

Room schema/migrations; Money/Currency; household/member;
accounts/categories; transactions; transfers/refunds; balance engine;
unit tests. Exit: correct local engine.

## Phase 2 --- Local MVP UI

Onboarding; Room-backed dashboard; transactions; quick-add;
accounts/categories; hybrid budget engine/UI; month filters;
Arabic/English baseline. Exit: one-phone offline MVP and debug APK.

## Phase 3 --- Two-device cloud

Firebase Auth; household invitation; Firestore schema/indexes/rules;
emulator tests; backend interfaces; outbox; WorkManager; remote cursor;
realtime; idempotency; conflicts; two-device tests. Exit: reliable
two-user shared data.

## Phase 4 --- Planning

Savings; recurring; rollover; activity; reports; notifications.
Status: built (savings goals, recurring items with confirm/skip, activity feed, yearly reports, daily reminders). Rollover was already part of budgets.

## Phase 5 --- Self-hosting

PostgreSQL migrations; Supabase Docker deployment; RLS; adapter;
migration tool; reconciliation tests. Do not start until canonical
schema and sync contract stabilize.

## Phase 6 --- Release

RTL/accessibility; JSON backup/restore; CSV import/export; performance;
security review; signed APK; install/upgrade guide.

## Dependencies

Money model before transaction UI; household before sync; Room before
repository; transaction invariants before reports; outbox before cloud
sync; security before backend exposure; Firebase stable before Supabase
migration; backup before production.

## First sprints

Sprint 1: project bootstrap, CI, Money/Currency, Room,
household/account/category/transaction entities, balance tests. Sprint
2: repositories, expense/income, transfer, dashboard, history,
quick-add, account/category UI. Sprint 3: budgets, category limits,
zero-based allocation, month selector, localization, backup baseline.
Sprint 4: Firebase Auth, invitation, rules, outbox, idempotency,
conflicts, two-device tests.
