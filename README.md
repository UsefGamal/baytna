# Baytna (بيتنا)

*Our money, our plans.*

Baytna is a free, offline-first Android household budgeting app for two spouses. Money is in Egyptian
pounds (stored as integer piastres), the UI is English and Arabic (RTL), and Room on the phone is the
source of truth. Firebase sync is the next milestone; this repository currently ships the **local MVP**.

## What works today (milestone 1: local MVP)

- **Onboarding**: household name, both members, starting balances for Cash and Bank, language.
- **Home**: month selector, swipeable per-account cards (each account's balance and activity, no summed total), income / expenses / net cash flow (transfers excluded,
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

Every change is written to Room together with a `sync_outbox` row in the same transaction, and every
update is version-checked, so the coming Firebase sync can upload exactly once and detect conflicts.

## Not in this milestone

Firebase Auth, invitations and two-phone sync; savings goals; recurring items; JSON backup/restore;
CSV import; split transactions; a signed release APK. See `docs/ROADMAP_AND_BACKLOG.md`.

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
