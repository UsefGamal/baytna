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
