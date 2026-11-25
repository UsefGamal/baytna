package com.baytna.domain.model

enum class TransactionType { INCOME, EXPENSE, TRANSFER, REFUND }

enum class Ownership { SHARED, PERSONAL }

enum class AccountType { CASH, BANK, DEBIT_CARD, SAVINGS, DIGITAL_WALLET, OTHER }

enum class CategoryType { INCOME, EXPENSE }

enum class MemberRole { OWNER, MEMBER }

enum class AllocationType { CATEGORY_LIMIT, SAVINGS, RESERVE, OTHER }

enum class BudgetPeriodType { WEEKLY, MONTHLY, CUSTOM }

enum class BudgetWarning { NONE, AT_75, AT_90, OVER_100 }
