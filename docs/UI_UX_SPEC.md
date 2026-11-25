# UI/UX Specification

## Visual direction

Premium, calm, minimal finance UI. Material 3; emerald accent `#16A36A`;
dark background `#101713`; dark surface `#19231D`; light background
`#F7F9F7`; text `#202A24`; income green; expense `#E36D6D`; warning
`#E6A23C`. Use semantic tokens, restrained rounded cards and readable
numbers.

## Navigation

Bottom destinations: Home, Transactions, Budgets, Insights, More.
Persistent quick-add action. More contains accounts, savings, recurring,
members, categories, backup and settings.

## Screens

-   Onboarding: welcome, sign-in, create/join household, language, EGP,
    initial accounts, categories, optional budget, invitation.
-   Home: month selector, household balance, income, expenses, net cash
    flow, budget progress, category summary, recent transactions,
    activity, sync status.
-   Transactions: date-grouped list, search and filters,
    add/edit/detail, payer and ownership labels, soft delete/restore.
-   Add transaction: amount keypad, type, category, account/destination,
    shared/personal, payer/owner, date, note, save. Default
    payer=current user, date=today, currency=household EGP. Target \<10
    seconds.
-   Budgets: period selector, category limits and zero-based allocation,
    allocated/actual/remaining, reserve, rollover, warnings at
    75/90/100%.
-   Accounts: list, balances, detail ledger, create/edit/archive,
    transfer.
-   Savings: goal cards, target/current/progress, contributions,
    withdrawals and estimated completion.
-   Insights: income/expense, category, monthly trend, member,
    shared/personal, budget performance and date filters.
-   More/settings: household, invitations, categories, recurring,
    export, appearance, language, notifications, app lock, sync status.

## Localization and accessibility

Android string resources, full RTL, mirrored navigation, locale-aware
currency/date, Arabic and Latin numeral testing, no concatenated
translated fragments, chart RTL testing. Touch targets, content
descriptions, font scaling, contrast, color-independent states,
confirmations and clear offline/error/conflict states.

## Acceptance

Expense entry \<10 seconds in normal flow; local save appears
immediately; sync status is truthful; payer and ownership are clear;
dashboard reconciles to transactions; Arabic has no clipping or
direction defects.
