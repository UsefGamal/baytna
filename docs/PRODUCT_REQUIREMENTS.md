# Product Requirements

## Vision

Baytna is a shared household finance app for a couple. It makes daily
expense entry quick, keeps both members informed, supports offline use
and gives a clear view of spending, budgets and savings.

## Users and visibility

Two initial members, extensible later. Both see all transaction details.
A transaction has: - `paid_by_user_id`: who paid; - `ownership`:
`shared` or `personal`; - `personal_owner_user_id`: whose personal
spending it represents, required for personal transactions.

Payer and personal owner may differ. Personal does not mean private.

## Product principles

Financial accuracy first; fast entry; offline-first; no silent data
loss; transparent household access; no ads or paywalls; data export;
replaceable backend; no SMS/bank scraping in MVP.

## Features

### Authentication and household

Email/password sign-in; create household; secure expiring single-use
invitation; join existing household; member roles/status. Second phone
joins the existing household.

### Dashboard

Selected month; household account balance; income; expenses; net cash
flow; budget utilization; category spending; recent transactions; sync
status. Clearly distinguish account balance, budget remaining, goal
progress and cash flow.

### Transactions

Income, expense, transfer, refund. Later split transactions. Fields:
UUID, household, account, optional destination account, category, type,
positive amount minor units, currency, payer, ownership, personal owner,
occurred timestamp, note, creator, created/updated timestamps, version,
soft delete and local sync state. Support
add/edit/view/search/filter/duplicate/soft-delete/restore.

### Accounts

Cash, bank, debit card, savings, digital wallet and other. Opening
balance, currency, owner, shared flag and active flag. Transfers update
both accounts and are excluded from income/expense totals.

### Categories

Housing, groceries, utilities, transport, food/delivery, health,
shopping, entertainment, family, subscriptions, personal,
savings/planning and other. Support parent category, icon, color,
income/expense type and archive.

### Hybrid budgets

Support category spending limits and zero-based allocations together.
Periods can be weekly, monthly or custom. Track allocation, rollover,
actual spending, remaining and percentage. Zero-based plan tracks
available planning income, category allocations, savings allocations and
reserve/unallocated amount. An intentional reserve is allowed.
Allocations are planning records, not transactions.

### Savings

Goals with target, current progress, currency, target date, owner,
contributions and withdrawals. Do not double-count contributions as
expenses and transfers.

### Recurring (later)

Weekly/monthly/yearly/custom. Expected occurrences are not posted
transactions until confirmed, unless an explicit auto-post option is
later designed.

### Insights

Income vs expenses, trends, category and member breakdown, shared vs
personal, budget performance, balances and comparisons.

### Export

JSON backup/restore, CSV transaction export/import, later PDF reports,
backup validation and optional encryption.

## Non-functional

Offline-first, secure household membership, Arabic RTL and English LTR,
light/dark themes, accessibility, no unnecessary permissions, durable
migrations, release APK.

## MVP exclusions

Bank integration, SMS parsing, receipt OCR, AI advisor, FX service,
complex debt/tax accounting and paid cloud functions.

## Definition of done

Two phones join one household; local transactions work offline; both see
all details; sync is idempotent; financial tests pass; transfers are
excluded from income/expense; budgets reconcile; unauthorized access is
denied; export works; APK installs.
