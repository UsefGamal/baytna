# Architecture

## Stack

Kotlin, Jetpack Compose, Material 3, MVVM, pragmatic Clean Architecture,
Room, WorkManager, Coroutines/Flow, Hilt, DataStore, Vico (or maintained
Compose chart library), Firebase Auth/Firestore initially,
Supabase/PostgreSQL later, Gradle Kotlin DSL and GitHub Actions. Verify
stable versions before pinning.

## Local-first

Room is the source of truth for UI reads. On a user mutation: validate
domain command; write entity and outbox row in the same Room
transaction; UI observes Room Flow and updates immediately; worker sends
operation; backend validates membership and expected version; client
acknowledges; incoming changes are pulled and applied to Room
transactionally.

Never block local entry on network. Never read UI data directly from
Firebase.

## Layers

-   Presentation: Compose, ViewModels, navigation, UI state.
-   Domain: pure Kotlin money, business rules, use cases and repository
    interfaces.
-   Data: repository implementations, Room, mapping and remote adapters.
-   Core: database, sync, backend contracts, design system, security
    helpers.

## Repository layout

``` text
baytna/
  README.md LICENSE CONTRIBUTING.md SECURITY.md
  settings.gradle.kts build.gradle.kts gradle/libs.versions.toml
  app/
  core/{common,model,database,datastore,network,sync,backend,security,design}/
  domain/{transactions,accounts,budgets,savings,recurring,reports}/
  data/{transactions,accounts,budgets,households,repositories}/
  feature/{auth,onboarding,household,dashboard,transactions,budgets,accounts,savings,recurring,insights,settings}/
  backend/{firebase,supabase}/
  database/{migrations,seed}/
  docs/ agents/ prompts/ .github/workflows/
```

Start with fewer Gradle modules if needed; preserve package boundaries.

## Backend portability

Define interfaces for auth, household membership, transactions,
accounts/categories/budgets, realtime change feed and invitations.
Implement Firebase first, Supabase later. They are alternative active
providers, not dual-write replicas. Migration requires export,
validation, import, authorization tests and provider switch.

## Suggested contracts

``` kotlin
interface TransactionRepository {
  fun observeTransactions(householdId: String, filter: TransactionFilter): Flow<List<Transaction>>
  suspend fun create(command: CreateTransaction)
  suspend fun update(command: UpdateTransaction)
  suspend fun softDelete(id: String, expectedVersion: Int)
}
interface SyncEngine {
  suspend fun syncPendingChanges()
  suspend fun pullRemoteChanges()
}
```

Package ID proposal: `com.baytna.app` (confirm before release). Propose
min SDK API 26+ unless target devices require otherwise. Use Room
migrations, never destructive production migrations. Store signing keys
outside source control.
