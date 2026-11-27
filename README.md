# Baytna (بيتنا)

*Our money, our plans.*

Baytna is a free, offline-first Android household budgeting app for two spouses. Money is in Egyptian
pounds (stored as integer piastres), the UI is English and Arabic (RTL), and Room on the phone is the
source of truth; Firebase keeps two phones in sync.

## What works today

- **Onboarding**: household name, both members, starting balances for Cash and Bank, language.
- **Home**: month selector, household balance, income / expenses / net cash flow (transfers excluded,
  refunds netted), budget progress, spending by category, recent transactions, truthful sync status.
- **Transactions**: income, expense, transfer, refund (capped at the original expense). Payer and
  shared/personal owner are independent. Search, type filters, date-grouped list, edit, duplicate,
  soft delete and restore.
- **Budgets** (hybrid): monthly category limits with 75 / 90 / 100% warnings, plus a zero-based plan
  (planned income, savings allocations, reserve, unallocated). Optional rollover of each category's
  leftover or overspend into the next month. Allocations are plans, never transactions.
- **Insights**: income vs expenses, 6-month trend, category breakdown, spending by payer, shared vs personal.
- **More**: accounts (balances, ledger, create/edit/archive), categories (create/edit/archive), members,
  "this phone belongs to", light/dark/system theme, English/Arabic, CSV export.
- **Two phones**: email sign-in, single-use expiring invite codes, offline-first Firebase sync with an
  outbox, realtime updates, and a conflict screen (Keep mine / Use theirs). Account deletion in the app.
- **Planning**: savings goals, recurring items and transfers (confirm or skip), activity feed, yearly
  report, daily reminders.
- **Data**: JSON backup and restore, CSV import and export.
- **Release**: signed APK and AAB built by `release.yml` (see `docs/RELEASING.md`).

Every change is written to Room together with a `sync_outbox` row in the same transaction, and every
update is version-checked, so uploads happen exactly once and concurrent edits become conflicts.

## Not built yet

Split transactions, self-hosting on Supabase, Play Store submission. Verified status with source paths:
`docs/CURRENT_STATE_AUDIT.md`. Plan: `docs/ROADMAP_AND_BACKLOG.md`.

## Build

Requirements: JDK 17+, Android SDK with platform 35.

```bash
./gradlew :domain:test                 # pure-Kotlin financial engine tests
./gradlew :app:testDebugUnitTest       # Room repository tests (Robolectric)
./gradlew :app:assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/android.yml`) runs all three on every push and pull request and uploads the
debug APK as the `baytna-debug-apk` artifact.

The debug build installs as `com.baytna.app.debug` next to any future release build.

## Layout

```
domain/   Pure Kotlin: Money, MoneyParser, BalanceCalculator, CashFlowCalculator, BudgetMath,
          TransactionRules, Cairo month periods. No Android dependencies.
app/      Android app: Room entities/DAOs, BaytnaRepository (validation + atomic outbox writes),
          DataStore settings, Jetpack Compose UI (Material 3), English + Arabic strings.
docs/     Product, architecture, data model, sync/security, UI, tests, roadmap (the handoff pack).
```

Decisions taken for this milestone are in `docs/DECISIONS.md`. Screen rules (touch targets, contrast, RTL, copy) are in `docs/UI_UX_RULES.md`.
