# Current State Audit

Date: 27 November 2026. Branch audited: `main` at `abcbec5` (PR #12 merged).

This audit checks an outside handoff ("Baytna Next Development Handoff", written from the old README)
against the code. The handoff treats Firebase sync as the next milestone; it is already built. The
README was stale and is fixed in the same change as this file.

## Checks run

| Check | Where | Result |
|---|---|---|
| `firestore-tests` (`npm ci && npm test`, Firestore emulator) | cloud session | 23 of 23 pass |
| `./gradlew :domain:test` | cloud session | not run: Maven Central returned HTTP 429 and `dl.google.com` is blocked by the session proxy |
| `:domain:test`, `:app:testDebugUnitTest`, `:app:assembleDebug` | GitHub Actions (`.github/workflows/android.yml`) | run on every push and pull request; see the PR checks |

Test counts in the tree: domain 53 (`domain/src/test/kotlin/com/baytna/domain/`), app 49 Robolectric
tests (`app/src/test/java/com/baytna/app/data/`), rules 23 (`firestore-tests/rules.test.mjs`).

## Handoff items against the code

Status: **Done** (built and tested), **Partial**, **Missing**.

### Local foundation

| Item | Status | Evidence |
|---|---|---|
| Money as integer piastres | Done | `domain/.../Money.kt`, `MoneyParser.kt`; `MoneyParserTest` |
| Transfers excluded from income/expense, atomic | Done | `CashFlow.kt`, `BalanceCalculator.kt`; `BalanceAndCashFlowTest`; `BaytnaRepository.saveTransaction` |
| Refunds capped at the original expense | Done | `TransactionRules.kt`; `TransactionRulesTest` |
| Budget allocations are plans, not transactions | Done | `Budget.kt`; `BudgetTest` |
| Savings not double-counted | Done | `Savings.kt`; `PlanningTest`; DECISIONS "Savings goals" |
| Cairo month boundaries | Done | `Periods.kt` |
| Change and outbox row in one Room transaction | Done | `BaytnaRepository` (`db.withTransaction`), `SyncOutboxEntity` in `data/db/Entities.kt` |
| Version-checked updates | Done | `expectedVersion` on `SyncOutboxEntity`; `SyncEngineTest` |
| Room schema export and migrations | Done | `BaytnaDatabase.kt` (version 3, `exportSchema = true`), `Migrations.kt` (`MIGRATION_1_2`, `MIGRATION_2_3`), `MigrationTest` |
| Outbox states | Done | `STATUS_PENDING`, `STATUS_CONFLICT`, `STATUS_FAILED` in `Entities.kt`. There is no separate "in progress" state; a run is serialized and an operation id makes a retried upload a no-op, so a crash mid-upload is safe without one. |

### Identity and household (handoff Phase 1)

| Item | Status | Evidence |
|---|---|---|
| Firebase email sign-in, sign-out, password reset | Done | `SyncManager.signIn/signUp/signOut/sendPasswordReset`, `FirebaseCloud.kt` |
| Invite codes: high entropy, stored only as SHA-256, single use, expiring, revocable | Done | `InviteCodes.kt`, `InviteCodesTest`; `firestore.rules` `match /invites/{hash}` |
| Atomic invite consumption, no replay | Done | `firestore.rules` `inviteJoin()` uses `get`/`getAfter` on the invite in the same write |
| Joining by guessing a household id | Blocked | membership create needs owner bootstrap or a valid invite; rules tests |
| Sign-out keeps local data | Done | `SyncManager.signOut` |
| Account deletion (in-app and web) | Done | `SyncManager.deleteAccount`, `docs/DELETE_ACCOUNT.md` |

### Data contract and rules (handoff Phase 2)

| Item | Status | Evidence |
|---|---|---|
| Every record scoped to a household, default deny | Done | `firestore.rules`; 23 emulator tests cover allowed and denied paths |
| Stable ids, versions, tombstones | Done | `data/sync/Records.kt`, `docs/SYNC_AND_SECURITY.md` |
| Written sync contract | Partial | `docs/SYNC_AND_SECURITY.md` and `Records.kt` cover it; there is no separate per-collection field list. The handoff's `FIREBASE_SYNC_CONTRACT.md`, `FIREBASE_SECURITY_MODEL.md` and `SYNC_STATE_MACHINE.md` would duplicate that doc, so they were not created. |

### Upload, pull, conflicts (handoff Phases 3 to 5)

| Item | Status | Evidence |
|---|---|---|
| WorkManager sync, network constraint, exponential backoff | Done | `SyncManager.requestSync` (30 s exponential), `schedulePeriodic` (6 h), `SyncWorker` |
| Idempotent upload by operation id | Done | `SyncEngine.push`; `SyncEngineTest` |
| Permanent failures visible, not retried forever | Done | `STATUS_FAILED` and rejected count in `PushSummary`; Cloud sync screen |
| Incremental pull with a durable cursor and overlap window | Done | `SyncEngine.pull`, `OVERLAP_MS`; cursor saved in the same Room transaction |
| No echo loop | Done | remote rows are applied without writing outbox rows (`SyncEngine.apply`) |
| Realtime updates | Done | `backend.listen` in `SyncManager.start`; cursor pull is the correctness path |
| New phone bootstrap | Done | `SyncEngine.downloadHousehold` |
| Conflicts kept and resolved by the user | Done | `STATUS_CONFLICT`; `SyncEngine.keepMine` / `useTheirs`; Cloud sync screen |
| Field-level merge of concurrent edits | Missing (by design) | Whole-record conflicts only. DECISIONS and `SYNC_AND_SECURITY.md` choose explicit user resolution for financial records. |

### Two-device testing (handoff Phase 6)

| Item | Status | Evidence |
|---|---|---|
| Engine scenarios with two fake phones | Done | `SyncEngineTest` with `FakeCloud` |
| Manual two-phone run on real devices | Partial | No written record of a full run. Steps are in `docs/TWO_DEVICE_TEST_PLAN.md` (new). |

### Features the handoff defers

| Feature | Status | Evidence |
|---|---|---|
| JSON backup and restore | Done | PR #12; `BackupTest` |
| Savings goals | Done | PR #6; `PlanningRepositoryTest` |
| Recurring items and transfers | Done | PRs #6 and #9 |
| CSV import | Done | PR #12 |
| Signed release APK and AAB | Done | `release.yml`, `docs/RELEASING.md`; v0.1.1 published |
| Split transactions | Missing | Only mentioned in `docs/PRODUCT_REQUIREMENTS.md` and `docs/TEST_PLAN.md` |

## Open work

1. Split transactions (changes budget and refund rules; needs the owner's approval of the rules).
2. Phase 5 self-hosting (Supabase). New backend, needs the owner's approval.
3. Play Store submission (listing text and privacy policy exist in `docs/`).
4. A recorded run of `docs/TWO_DEVICE_TEST_PLAN.md` on two real phones.
