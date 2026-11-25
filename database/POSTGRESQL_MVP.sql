-- Baytna MVP PostgreSQL/Supabase schema draft.
-- NOT production-ready until RLS, policies, triggers, RPCs and tests are added.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE households (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 name TEXT NOT NULL,
 currency CHAR(3) NOT NULL DEFAULT 'EGP',
 locale TEXT NOT NULL DEFAULT 'en-EG',
 timezone TEXT NOT NULL DEFAULT 'Africa/Cairo',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE household_members (
 household_id UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
 user_id UUID NOT NULL,
 role TEXT NOT NULL DEFAULT 'member' CHECK(role IN ('owner','member')),
 status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('invited','active','removed')),
 joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(household_id,user_id)
);
CREATE TABLE invitations (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
 created_by UUID NOT NULL,
 token_hash TEXT NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL,
 used_at TIMESTAMPTZ,
 revoked_at TIMESTAMPTZ
);
CREATE TABLE accounts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 name TEXT NOT NULL,
 type TEXT NOT NULL CHECK(type IN ('cash','bank','debit_card','savings','digital_wallet','other')),
 currency CHAR(3) NOT NULL DEFAULT 'EGP',
 opening_balance_minor BIGINT NOT NULL DEFAULT 0,
 owner_user_id UUID,
 is_shared BOOLEAN NOT NULL DEFAULT TRUE,
 is_active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(id,household_id)
);
CREATE TABLE categories (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 parent_id UUID,
 name TEXT NOT NULL,
 icon TEXT,
 color TEXT,
 type TEXT NOT NULL CHECK(type IN ('income','expense')),
 is_system BOOLEAN NOT NULL DEFAULT FALSE,
 is_active BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE(id,household_id),
 FOREIGN KEY(parent_id,household_id) REFERENCES categories(id,household_id)
);
CREATE TABLE transactions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 account_id UUID NOT NULL,
 destination_account_id UUID,
 category_id UUID,
 type TEXT NOT NULL CHECK(type IN ('income','expense','transfer','refund')),
 amount_minor BIGINT NOT NULL CHECK(amount_minor > 0),
 currency CHAR(3) NOT NULL DEFAULT 'EGP',
 paid_by_user_id UUID NOT NULL,
 ownership TEXT NOT NULL DEFAULT 'shared' CHECK(ownership IN ('shared','personal')),
 personal_owner_user_id UUID,
 occurred_at TIMESTAMPTZ NOT NULL,
 note TEXT,
 transfer_group_id UUID,
 original_transaction_id UUID,
 created_by UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 version INTEGER NOT NULL DEFAULT 1 CHECK(version > 0),
 deleted_at TIMESTAMPTZ,
 FOREIGN KEY(account_id,household_id) REFERENCES accounts(id,household_id),
 FOREIGN KEY(destination_account_id,household_id) REFERENCES accounts(id,household_id),
 FOREIGN KEY(category_id,household_id) REFERENCES categories(id,household_id),
 FOREIGN KEY(original_transaction_id,household_id) REFERENCES transactions(id,household_id),
 UNIQUE(id,household_id),
 CHECK((ownership='shared' AND personal_owner_user_id IS NULL) OR
       (ownership='personal' AND personal_owner_user_id IS NOT NULL)),
 CHECK((type='transfer' AND destination_account_id IS NOT NULL
        AND destination_account_id <> account_id AND category_id IS NULL)
       OR (type <> 'transfer' AND destination_account_id IS NULL))
);
CREATE TABLE transaction_splits (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 transaction_id UUID NOT NULL,
 category_id UUID NOT NULL,
 amount_minor BIGINT NOT NULL CHECK(amount_minor > 0),
 note TEXT,
 FOREIGN KEY(transaction_id,household_id) REFERENCES transactions(id,household_id),
 FOREIGN KEY(category_id,household_id) REFERENCES categories(id,household_id)
);
CREATE TABLE budgets (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 name TEXT NOT NULL,
 mode TEXT NOT NULL CHECK(mode IN ('category_limits','zero_based','hybrid')),
 period_type TEXT NOT NULL CHECK(period_type IN ('weekly','monthly','custom')),
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 rollover_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('draft','active','closed')),
 CHECK(end_date >= start_date)
);
CREATE TABLE budget_allocations (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 budget_id UUID NOT NULL REFERENCES budgets(id),
 category_id UUID,
 allocation_type TEXT NOT NULL DEFAULT 'category'
   CHECK(allocation_type IN ('category','savings','reserve','other')),
 allocated_minor BIGINT NOT NULL DEFAULT 0 CHECK(allocated_minor >= 0),
 rollover_minor BIGINT NOT NULL DEFAULT 0,
 label TEXT,
 FOREIGN KEY(category_id,household_id) REFERENCES categories(id,household_id)
);
CREATE TABLE savings_goals (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 name TEXT NOT NULL,
 target_minor BIGINT NOT NULL CHECK(target_minor > 0),
 currency CHAR(3) NOT NULL DEFAULT 'EGP',
 owner_user_id UUID,
 target_date DATE,
 status TEXT NOT NULL DEFAULT 'active'
);
CREATE TABLE goal_contributions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 goal_id UUID NOT NULL REFERENCES savings_goals(id),
 amount_minor BIGINT NOT NULL CHECK(amount_minor > 0),
 contribution_type TEXT NOT NULL CHECK(contribution_type IN ('contribution','withdrawal')),
 transaction_id UUID,
 contributed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE recurring_rules (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 category_id UUID,
 account_id UUID NOT NULL REFERENCES accounts(id),
 type TEXT NOT NULL CHECK(type IN ('income','expense')),
 amount_minor BIGINT NOT NULL CHECK(amount_minor > 0),
 currency CHAR(3) NOT NULL DEFAULT 'EGP',
 frequency TEXT NOT NULL CHECK(frequency IN ('daily','weekly','monthly','yearly','custom')),
 interval_count INTEGER NOT NULL DEFAULT 1 CHECK(interval_count > 0),
 next_due_at TIMESTAMPTZ NOT NULL,
 is_active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE recurring_occurrences (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 rule_id UUID NOT NULL REFERENCES recurring_rules(id),
 scheduled_for TIMESTAMPTZ NOT NULL,
 status TEXT NOT NULL DEFAULT 'expected' CHECK(status IN ('expected','completed','skipped')),
 transaction_id UUID,
 UNIQUE(rule_id,scheduled_for)
);
CREATE TABLE activity_logs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 actor_user_id UUID NOT NULL,
 entity_type TEXT NOT NULL,
 entity_id UUID NOT NULL,
 action TEXT NOT NULL,
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE sync_changes (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 household_id UUID NOT NULL REFERENCES households(id),
 entity_type TEXT NOT NULL,
 entity_id UUID NOT NULL,
 operation TEXT NOT NULL CHECK(operation IN ('create','update','delete')),
 version INTEGER NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(entity_type,entity_id,version)
);
CREATE INDEX idx_tx_household_date ON transactions(household_id,occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_tx_account_date ON transactions(account_id,occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_tx_category_date ON transactions(category_id,occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_sync_household_created ON sync_changes(household_id,created_at);

-- TODO: enable RLS on all exposed tables; add active-member policies;
-- add composite household FKs for all relations; implement atomic transfer/refund
-- RPCs; idempotency table; expected-version checks; audit triggers;
-- invitation consume function; split-sum/refund-limit validation.
