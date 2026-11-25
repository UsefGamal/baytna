# Data Model and Financial Invariants

## Conventions

UUID IDs; integer minor units (`Long`/`BIGINT`); EGP has 100 piastres
per pound; ISO currency code; UTC instants for timestamps; local
calendar dates for budget periods; `Africa/Cairo` display timezone; soft
delete; household-scoped records; actor and version metadata.

## Entities

-   `users`: id, display_name, email, avatar_url, created_at. Auth
    credentials stay in provider.
-   `households`: id, name, currency, locale, timezone, timestamps.
-   `household_members`: household_id, user_id, role, status, joined_at;
    unique household/user.
-   `invitations`: id, household_id, created_by, token_hash, expires_at,
    used_at, revoked_at.
-   `accounts`: id, household_id, name, type, currency,
    opening_balance_minor, owner_user_id, is_shared, is_active.
-   `categories`: id, household_id, parent_id, name, icon, color, type,
    is_system, is_active.
-   `transactions`: id, household_id, account_id,
    destination_account_id, category_id, type, amount_minor, currency,
    paid_by_user_id, ownership, personal_owner_user_id, occurred_at,
    note, transfer_group_id, original_transaction_id, created_by,
    created_at, updated_at, version, deleted_at.
-   `transaction_splits`: id, transaction_id, category_id, amount_minor,
    note; split sum equals parent amount.
-   `budgets`: id, household_id, name, mode, period_type, start_date,
    end_date, rollover_enabled, status.
-   `budget_allocations`: id, budget_id, category_id (nullable for
    reserve/savings), allocation_type, allocated_minor, rollover_minor,
    label.
-   `savings_goals`: id, household_id, name, target_minor, currency,
    owner_user_id, target_date, status.
-   `goal_contributions`: id, goal_id, amount_minor, contribution_type,
    transaction_id, contributed_at.
-   `recurring_rules`: id, household_id, category_id, account_id, type,
    amount_minor, frequency, interval, next_due_at, active.
-   `recurring_occurrences`: id, rule_id, scheduled_for, status,
    transaction_id; unique rule/date.
-   `activity_logs`: id, household_id, actor_user_id, entity_type,
    entity_id, action, metadata, created_at.
-   `attachments`: id, household_id, transaction_id, storage_key,
    mime_type, size_bytes, created_by.
-   Local-only `sync_outbox`: operation ID, entity type/id, operation,
    payload, expected version, status, retry count, last error.
-   Local-only `sync_cursors`: last pulled remote change cursor per
    household/provider.

## Invariants

1.  Never use Float/Double for money.
2.  Store positive amount; type determines accounting effect.
3.  Transfers are not income or expenses.
4.  Transfer debit/credit is atomic.
5.  Refund references original where applicable and follows a defined
    cap policy.
6.  Budget allocation is not a transaction.
7.  Savings allocation is not automatically an expense.
8.  Do not double-count savings contributions.
9.  Balance = opening + income + refunds - expenses + transfer in -
    transfer out.
10. Scheduled items are not posted until confirmed by default.
11. Personal classification is visible to both members.
12. Payer and personal owner are independent.
13. Split sum equals parent amount.
14. Related records must belong to same household.
15. Updates are version checked; no silent financial last-write-wins.

## Calculations

Net cash flow = income - expenses (transfers excluded). Savings/net
cash-flow rate = (income - expenses) / income \* 100 only if income \>
0; label accurately. Budget remaining = allocation + rollover - actual
spending. Zero-based unallocated = planning income - category
allocations - savings allocations - other planned uses. Allow reserve.

## Transfer

Prefer one transfer aggregate with source, destination and amount,
committed atomically. If represented as paired ledger entries, same
transfer group and atomic lifecycle.
