# Firebase setup (milestone 2)

Baytna uses Firebase Auth (email and password) and Cloud Firestore. The free Spark plan is enough: there are no
Cloud Functions, and security rules enforce membership, versions, idempotency and invitations.

## One-time console steps

1. Create a Firebase project.
2. Add an Android app with package `com.baytna.app.debug` (the debug APK). Add `com.baytna.app` too before the first
   release build. No SHA-1 is needed for email/password sign-in.
3. Authentication → Sign-in method → enable **Email/Password**.
4. Firestore Database → create in **production mode** (for example `europe-west1`).
5. Firestore → Rules → paste [`firestore.rules`](../firestore.rules) → Publish. Repeat whenever that file changes.
6. `app/google-services.json` is committed (Firebase app config is not a secret; the security rules protect the data).
   A `GOOGLE_SERVICES_JSON` Actions secret, if set, overrides it in CI. Without either, the APK builds local-only.

No indexes need to be created: every query uses a single field.

## Data layout

| Path | Contents |
|---|---|
| `users/{uid}` | `householdId`, `memberId`: which household the account belongs to (used to restore). |
| `households/{hid}` | `ownerUid`, `createdAt`. |
| `households/{hid}/memberships/{uid}` | `memberId` (local member profile), `role`, `status`, `inviteId`. |
| `households/{hid}/entities/{type}_{id}` | One per synced row: `version`, `deleted`, `payload` (the row as JSON), `updatedBy`, `serverUpdatedAt`, `lastOpId`. |
| `households/{hid}/ops/{operationId}` | Processed outbox operations (write-once), for idempotent retries. |
| `invites/{sha256(code)}` | `householdId`, `memberId`, `expiresAt`, `revoked`, `usedBy`. The code is never stored. |

## Tests

`firestore-tests/` runs the rules against the Firestore emulator (`npm ci && npm test`, needs Java 11+). CI runs it in
the `firestore-rules` job.
