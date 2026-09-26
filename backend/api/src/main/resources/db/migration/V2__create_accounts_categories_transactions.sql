CREATE TABLE categories (
    id             UUID PRIMARY KEY,
    user_id        UUID REFERENCES users (id) ON DELETE CASCADE,
    slug           VARCHAR(64),
    name           VARCHAR(60) NOT NULL,
    category_group VARCHAR(20) NOT NULL
        CHECK (category_group IN ('INCOME', 'ESSENTIALS', 'LIFESTYLE', 'SAVINGS', 'DEBT', 'TRANSFERS')),
    icon           VARCHAR(40) NOT NULL,
    colour         VARCHAR(7)  NOT NULL CHECK (colour ~ '^#[0-9A-F]{6}$'),
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT categories_system_slug CHECK ((user_id IS NULL) = (slug IS NOT NULL))
);

CREATE UNIQUE INDEX categories_system_slug_key ON categories (slug) WHERE user_id IS NULL;
CREATE UNIQUE INDEX categories_user_name_key ON categories (user_id, lower(name)) WHERE user_id IS NOT NULL;

CREATE TABLE accounts (
    id                        UUID PRIMARY KEY,
    user_id                   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                      VARCHAR(100) NOT NULL,
    account_type              VARCHAR(20)  NOT NULL
        CHECK (account_type IN ('EVERYDAY', 'SAVINGS', 'CREDIT_CARD', 'LOAN', 'KIWISAVER', 'INVESTMENT', 'CASH', 'OTHER')),
    institution               VARCHAR(100),
    current_balance_cents     BIGINT       NOT NULL DEFAULT 0,
    is_liquid                 BOOLEAN      NOT NULL,
    include_in_emergency_fund BOOLEAN      NOT NULL DEFAULT FALSE,
    managed_by                VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (managed_by IN ('USER', 'BANK_FEED')),
    archived_at               TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL
);

CREATE INDEX accounts_user_id_idx ON accounts (user_id);

CREATE TABLE transactions (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id      UUID         NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    posted_on       DATE         NOT NULL,
    amount_cents    BIGINT       NOT NULL,
    description     VARCHAR(500) NOT NULL,
    merchant        VARCHAR(200),
    category_id     UUID REFERENCES categories (id),
    category_source VARCHAR(20) CHECK (category_source IN ('USER', 'RULE', 'PROVIDER')),
    source          VARCHAR(20)  NOT NULL CHECK (source IN ('MANUAL', 'CSV_IMPORT', 'BANK_FEED')),
    external_id     VARCHAR(100),
    is_transfer     BOOLEAN      NOT NULL DEFAULT FALSE,
    notes           VARCHAR(1000),
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT transactions_category_source CHECK ((category_id IS NULL) = (category_source IS NULL))
);

CREATE INDEX transactions_user_posted_on_idx ON transactions (user_id, posted_on DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX transactions_account_id_idx ON transactions (account_id);
CREATE INDEX transactions_category_id_idx ON transactions (category_id);
CREATE UNIQUE INDEX transactions_account_external_id_key ON transactions (account_id, external_id) WHERE external_id IS NOT NULL;

INSERT INTO categories (id, user_id, slug, name, category_group, icon, colour, created_at, updated_at)
SELECT gen_random_uuid(), NULL, slug, name, category_group, icon, colour, now(), now()
FROM (VALUES
    ('salary', 'Salary & wages', 'INCOME', 'briefcase', '#16A34A'),
    ('government-support', 'Government support', 'INCOME', 'landmark', '#15803D'),
    ('interest', 'Interest', 'INCOME', 'percent', '#22C55E'),
    ('other-income', 'Other income', 'INCOME', 'coins', '#4ADE80'),
    ('rent', 'Rent', 'ESSENTIALS', 'home', '#2563EB'),
    ('mortgage', 'Mortgage', 'ESSENTIALS', 'house', '#1D4ED8'),
    ('rates', 'Rates', 'ESSENTIALS', 'receipt', '#3B82F6'),
    ('power-gas', 'Power & gas', 'ESSENTIALS', 'zap', '#F59E0B'),
    ('internet-phone', 'Internet & phone', 'ESSENTIALS', 'wifi', '#0EA5E9'),
    ('groceries', 'Groceries', 'ESSENTIALS', 'shopping-cart', '#10B981'),
    ('fuel', 'Fuel', 'ESSENTIALS', 'fuel', '#EF4444'),
    ('public-transport', 'Public transport', 'ESSENTIALS', 'bus', '#8B5CF6'),
    ('vehicle', 'Vehicle costs', 'ESSENTIALS', 'car', '#DC2626'),
    ('insurance', 'Insurance', 'ESSENTIALS', 'shield', '#6366F1'),
    ('health', 'Health', 'ESSENTIALS', 'heart-pulse', '#EC4899'),
    ('childcare', 'Childcare', 'ESSENTIALS', 'baby', '#F472B6'),
    ('education', 'Education', 'ESSENTIALS', 'graduation-cap', '#7C3AED'),
    ('bank-fees', 'Bank fees', 'ESSENTIALS', 'landmark', '#64748B'),
    ('eating-out', 'Eating out', 'LIFESTYLE', 'utensils', '#F97316'),
    ('takeaways', 'Takeaways', 'LIFESTYLE', 'pizza', '#FB923C'),
    ('subscriptions', 'Subscriptions', 'LIFESTYLE', 'repeat', '#A855F7'),
    ('shopping', 'Shopping', 'LIFESTYLE', 'shopping-bag', '#E11D48'),
    ('clothing', 'Clothing', 'LIFESTYLE', 'shirt', '#DB2777'),
    ('entertainment', 'Entertainment', 'LIFESTYLE', 'ticket', '#D946EF'),
    ('travel', 'Travel', 'LIFESTYLE', 'plane', '#06B6D4'),
    ('personal-care', 'Personal care', 'LIFESTYLE', 'sparkles', '#F43F5E'),
    ('gifts-donations', 'Gifts & donations', 'LIFESTYLE', 'gift', '#E879F9'),
    ('kiwisaver', 'KiwiSaver', 'SAVINGS', 'piggy-bank', '#0D9488'),
    ('savings', 'Savings', 'SAVINGS', 'vault', '#14B8A6'),
    ('debt-repayments', 'Debt repayments', 'DEBT', 'credit-card', '#B91C1C'),
    ('transfers', 'Transfers', 'TRANSFERS', 'arrow-left-right', '#94A3B8')
) AS seed (slug, name, category_group, icon, colour);
