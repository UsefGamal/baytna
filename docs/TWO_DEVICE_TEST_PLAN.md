# Two-Phone Test Plan

Manual run on two real Android phones (A and B) with the release or debug APK and two email accounts.
A third account (C) checks isolation. Record the date, APK version and pass/fail for each step at the
bottom. `docs/TEST_PLAN.md` lists the same cases at a higher level; engine-level versions run in
`SyncEngineTest`.

## Setup

1. Phone A: fresh install, onboarding, add a few transactions. Cloud sync → create account → turn on sync.
2. Phone A: invite the partner; copy the code.
3. Phone B: fresh install, Cloud sync → create account → join with the code.
   Expect: B shows A's household, balances equal on both phones.
4. Phone B: try the same code again from another account (C). Expect: rejected as used.

## Scenarios

| # | Steps | Expect |
|---|---|---|
| 1 | Both phones in airplane mode. A adds an expense, B adds an income. Reconnect both. | Both rows on both phones; totals match. |
| 2 | A creates a transfer offline, reconnects. | One transfer on B; both account balances match. |
| 3 | A records a partial refund of an existing expense. | B shows the refund linked to the expense; refundable amount matches. |
| 4 | Both offline, both edit the same transaction differently, reconnect. | The second phone to upload shows a conflict. Keep mine or Use theirs; both phones end with the chosen version. |
| 5 | Both offline, A deletes a transaction, B edits it, reconnect. | Conflict shown; no silent loss. |
| 6 | A deletes then restores a transaction. | B follows both steps; balances return. |
| 7 | Turn on airplane mode in the middle of a sync on A (many pending rows). Reconnect. | Every row uploads once; no duplicates on B. |
| 8 | Force-stop A from Android settings while it is syncing, reopen. | Pending rows upload once. |
| 9 | Clear app data on B (or reinstall), sign in, restore. | B gets the full household back. |
| 10 | Sign out on A, sign in as C on the same phone. | C cannot see the household; A's local data is still on the phone. |
| 11 | Set A to Arabic and B to English. | Same amounts and dates; Arabic screens are right to left. |
| 12 | Both phones confirm the same recurring occurrence offline. | One transaction after sync; the other phone sees a conflict, not a second posting. |
| 13 | A revokes an unused invite; try it on another phone. | Rejected. |

## Results

| Date | APK | Steps passed | Notes |
|---|---|---|---|
| | | | |
