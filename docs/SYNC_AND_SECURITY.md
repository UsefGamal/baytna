# Synchronization and Security

## Outbox

Every local mutation and outbox insertion happen in one Room
transaction. Outbox operation includes UUID, household, entity type/id,
create/update/delete, payload/reference, expected version, timestamp,
attempts, status and error.

Workflow: local validation -\> Room write + outbox -\> immediate UI
update -\> WorkManager upload -\> backend membership/schema/version
validation -\> acknowledgement -\> pull remote changes by cursor -\>
apply to Room transactionally.

Use exponential backoff with jitter. Trigger after local writes, app
start, login and network restoration. Periodic work is backup, not the
only sync mechanism.

## Idempotency

Stable client UUID for entity; unique operation UUID for mutation;
backend recognizes processed operations. Retrying must not duplicate
records or money movement.

## Conflicts

Every record has version. Client sends expected version. On mismatch,
return current server version; preserve attempted local edit and show
conflict. User resolves financial conflicts explicitly. Separate new
transactions do not conflict just because they share a household.
Deletions use versioned tombstones.

## Realtime

Realtime listeners accelerate updates but are not correctness mechanism.
Cursor-based reconciliation recovers missed events.

## Security

-   Firebase Auth initially; never store passwords.
-   Active household membership checked on every read/write.
-   Client-supplied household ID is not proof of membership.
-   Invitation token high entropy, hashed at rest, expiry, single use,
    revocable and rate limited.
-   Firestore deny by default; validate auth, membership, immutable
    fields, creator, household and allowed fields.
-   Complex transfers, invitation consumption and version-checked writes
    may require trusted backend logic.
-   Supabase RLS enabled on every exposed table; no service-role key in
    APK.
-   No Admin credentials in app or repository.
-   Both household members see transaction details by product decision.
-   No ad network transmission of financial data; no SMS scraping in
    MVP.

## Portability

Firebase and Supabase are alternatives, never dual-write. Migration:
freeze/maintenance mode; export canonical records; validate
counts/relations/totals; import; test membership and authorization;
switch provider; reconcile sync cursors; retain backup until accepted.
