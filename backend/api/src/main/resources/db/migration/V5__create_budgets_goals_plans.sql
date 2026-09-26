CREATE TABLE budgets (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX budgets_user_id_idx ON budgets (user_id);
CREATE UNIQUE INDEX budgets_one_active_per_user ON budgets (user_id) WHERE is_active;

CREATE TABLE budget_lines (
    id          UUID PRIMARY KEY,
    budget_id   UUID        NOT NULL REFERENCES budgets (id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories (id) ON DELETE CASCADE,
    limit_cents BIGINT      NOT NULL CHECK (limit_cents >= 0),
    rationale   VARCHAR(300),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX budget_lines_category_key ON budget_lines (budget_id, category_id) WHERE category_id IS NOT NULL;
CREATE UNIQUE INDEX budget_lines_uncategorised_key ON budget_lines (budget_id) WHERE category_id IS NULL;

CREATE TABLE goals (
    id                         UUID PRIMARY KEY,
    user_id                    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                       VARCHAR(100) NOT NULL,
    goal_type                  VARCHAR(20)  NOT NULL
        CHECK (goal_type IN ('CAR', 'HOUSE_DEPOSIT', 'TRAVEL', 'EDUCATION', 'PURCHASE', 'WEDDING', 'CUSTOM')),
    target_cents               BIGINT       NOT NULL CHECK (target_cents > 0),
    target_date                DATE,
    priority                   INTEGER      NOT NULL DEFAULT 2 CHECK (priority BETWEEN 1 AND 3),
    monthly_contribution_cents BIGINT       NOT NULL DEFAULT 0 CHECK (monthly_contribution_cents >= 0),
    linked_account_id          UUID REFERENCES accounts (id) ON DELETE SET NULL,
    status                     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'PAUSED', 'ACHIEVED', 'ARCHIVED')),
    achieved_at                TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ  NOT NULL,
    updated_at                 TIMESTAMPTZ  NOT NULL
);

CREATE INDEX goals_user_id_idx ON goals (user_id);

CREATE TABLE goal_contributions (
    id             UUID PRIMARY KEY,
    goal_id        UUID         NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount_cents   BIGINT       NOT NULL CHECK (amount_cents <> 0),
    contributed_on DATE         NOT NULL,
    note           VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX goal_contributions_goal_id_idx ON goal_contributions (goal_id, contributed_on);

CREATE TABLE purchase_plans (
    id                UUID PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    item_name         VARCHAR(100) NOT NULL,
    price_cents       BIGINT       NOT NULL CHECK (price_cents > 0),
    desired_date      DATE,
    funding           VARCHAR(10)  NOT NULL CHECK (funding IN ('CASH', 'FINANCE')),
    deposit_cents     BIGINT CHECK (deposit_cents >= 0),
    loan_rate         NUMERIC(6, 5) CHECK (loan_rate BETWEEN 0 AND 0.5),
    loan_term_months  INTEGER CHECK (loan_term_months BETWEEN 1 AND 360),
    loan_fees_cents   BIGINT CHECK (loan_fees_cents >= 0),
    first_home        BOOLEAN      NOT NULL DEFAULT FALSE,
    goal_id           UUID REFERENCES goals (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT purchase_plans_finance CHECK (
        funding = 'CASH' OR (deposit_cents IS NOT NULL AND loan_rate IS NOT NULL AND loan_term_months IS NOT NULL))
);

CREATE INDEX purchase_plans_user_id_idx ON purchase_plans (user_id);
