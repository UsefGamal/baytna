# Decisions for milestone 1 (local MVP)

| Topic | Decision | Why |
|---|---|---|
| Modules | `:domain` (pure Kotlin) + `:app`. Package boundaries follow the architecture doc. | The spec allows fewer Gradle modules at first. Keeps the financial engine testable on the JVM. |
| DI | Manual `AppContainer` instead of Hilt. | Small graph; avoids an annotation processor until the sync layer adds real wiring needs. Easy to swap. |
| Charts | Simple Compose bars instead of Vico. | Enough for MVP insights; avoids a dependency until richer charts are designed. |
| Refunds | Netted against expenses (and per category). Total refunds of an expense are capped at its amount. An expense with active refunds cannot be deleted or reduced below the refunded total. | The data model asks for a defined cap policy. |
| Transfers | One aggregate row with source and destination (`transferGroupId = id`). | Atomic by construction; the data model prefers this. |
| Rollover | Optional per budget. When a new month is created from the previous one, each category limit carries last month's remaining amount (negative if overspent). | Matches "allocation + rollover − actual". |
| Members | Local profiles until Firebase Auth. "This phone belongs to" sets the default payer. | Auth and invitations are milestone 2. |
| Sync status | Shows the number of queued outbox operations and says cloud sync is not set up. | The UI spec requires truthful sync status. |
| Database | Room schema version 1 exported to `app/schemas/`. No destructive migrations. | Future versions must ship explicit migrations. |
| Application id | `com.baytna.app` (debug: `com.baytna.app.debug`). | Proposed in the architecture doc; confirm before release. |
| minSdk | 26. | Proposed in the architecture doc. |

# Decisions for milestone 2 (Firebase sign-in, invitations, two-phone sync)

| Topic | Decision | Why |
|---|---|---|
| Sign-in | Firebase Auth with email and password. | Google sign-in needs a stable signing key registered in Firebase; CI debug builds get a new key each run. Can be added once there is a release keystore. |
| Backend logic | Security rules only, no Cloud Functions. | Keeps the project on the free plan. Invite consumption and version checks are enforced by rules using `get`/`getAfter` in the same atomic write. |
| Record storage | One Firestore document per row (`entities/{type}_{id}`) holding the row as a JSON string plus a version envelope. | One collection gives one pull cursor (`serverUpdatedAt`) and one realtime listener; payload validation stays in the app, envelope validation in rules. |
| Push | One Firestore transaction per outbox operation: checks `ops/{operationId}` (already applied), checks the expected version, writes the row and the op id together. Rules require the op doc in the same write and refuse a replay. | Idempotent retries; no duplicated money movement. |
| Pull | Query `serverUpdatedAt > cursor − 60 s`, apply parents first in one Room transaction, skip rows with waiting local operations or an equal/newer local version. Rows whose parent is missing are retried next pull. | Overlap covers commit-time skew; applying is idempotent. Realtime listener only triggers pulls. |
| Conflicts | A version mismatch marks the operation CONFLICT and blocks later operations on that row. The user picks "Keep mine" (re-queued on top of the cloud version) or "Use the cloud copy". Rejected operations (security rules) are shown the same way. | Spec: the user resolves financial conflicts explicitly. New rows never conflict. |
| Duplicate month budgets | If both phones create the same month's budget before syncing, the budget with the smaller id wins on both phones; the other one's lines are dropped locally. | Keeps the unique (household, period, start) index; rare and deterministic. |
| Invitations | 20-character Crockford base32 code (100 bits), shown once and shared from the phone. Stored as a salted SHA-256 hash, valid 7 days (rules cap at 8), single use, revocable. Each invite targets an existing member profile, so past entries keep their payer. | Spec: high entropy, hashed at rest, expiry, single use, revocable. Guessing is infeasible, which stands in for rate limiting without a backend. |
| Joining | Joining (or restoring) replaces any household on the phone after a confirmation, then downloads the shared one. | Room holds one household per phone. |
| Sync triggers | WorkManager unique work after local writes (debounced 1 s), on app start and sign-in, network-constrained with exponential backoff; a 6-hour periodic job as backup; a realtime listener while the app runs. | Spec: periodic work is backup, not the only mechanism. |
| Old outbox rows | Operations queued by milestone 1 lack `createdAt`/`sortOrder`; the engine fills them from the current row at upload. | Lossless upload without a schema migration. |
| Room schema | Unchanged (still version 1). Conflict state uses the existing outbox `status` and `lastError` columns. | No migration risk for existing installs. |
