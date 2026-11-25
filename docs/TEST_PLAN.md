# Test Plan and Release Gates

## Tools

JUnit, coroutine test utilities, Room tests, repository tests, Firebase
Emulator Suite, Supabase local stack, Compose UI tests and end-to-end
two-device tests.

## Financial tests

-   balances for opening, income, expense, refund, transfers and deleted
    rows;
-   transfers debit/credit correctly and do not affect income/expense;
-   duplicate transfer operation does not duplicate;
-   budget allocation, rollover, negative remaining, zero-based
    unallocated and reserve;
-   budget allocation does not change account balance;
-   savings allocation is not an expense;
-   shared/personal totals and payer/owner distinction;
-   refund policy and caps;
-   split sums reconcile.

## Offline/sync

1.  Create offline and restart; data persists.
2.  Reconnect; upload exactly once.
3.  Retry same operation; no duplicate.
4.  Two users create distinct expenses; both retained.
5.  Both edit same transaction; conflict detected.
6.  Delete vs edit; tombstone retained and conflict surfaced.
7.  Kill app mid-upload; safe retry.
8.  Server commit but lost acknowledgement; no duplicate.
9.  Miss realtime event; cursor pull recovers.
10. Backend unavailable; local history remains accessible.
11. Provider migration follows documented procedure.

## Security

Unauthenticated denied; non-member denied; member cannot arbitrarily
change roles; invitation single-use/expiry; creator cannot be forged;
household cannot be changed; cross-household references rejected;
secrets absent from APK/Git; RLS and Firestore rules tested with
multiple identities.

## UI

Create household; second user joins; add income/expense/transfer; month
switch; budget; Arabic RTL; themes; empty/error/offline/conflict states.

## Release gates

All financial tests pass; migration tests pass; authorization tests
pass; two-device sync passes; backup/restore passes; CI APK build
succeeds; signing secrets protected; no critical data-loss defect.
