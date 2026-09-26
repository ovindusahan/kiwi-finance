CREATE TABLE income_sources (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    income_type  VARCHAR(20)  NOT NULL
        CHECK (income_type IN ('SALARY', 'WAGES', 'BENEFIT', 'SELF_EMPLOYED', 'OTHER')),
    amount_cents BIGINT       NOT NULL CHECK (amount_cents > 0),
    amount_basis VARCHAR(10)  NOT NULL CHECK (amount_basis IN ('GROSS', 'NET')),
    frequency    VARCHAR(20)  NOT NULL
        CHECK (frequency IN ('WEEKLY', 'FORTNIGHTLY', 'FOUR_WEEKLY', 'MONTHLY', 'ANNUALLY')),
    tax_code     VARCHAR(4) CHECK (tax_code IN ('M', 'ME', 'SB', 'S', 'SH', 'ST', 'SA')),
    starts_on    DATE,
    ends_on      DATE,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT income_sources_dates CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);

CREATE INDEX income_sources_user_id_idx ON income_sources (user_id);

CREATE TABLE categorisation_rules (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    match_type  VARCHAR(20)  NOT NULL CHECK (match_type IN ('CONTAINS', 'STARTS_WITH', 'EQUALS')),
    pattern     VARCHAR(100) NOT NULL,
    category_id UUID         NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    priority    INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX categorisation_rules_user_id_idx ON categorisation_rules (user_id);

CREATE TABLE import_batches (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id      UUID         NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    format          VARCHAR(30)  NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    row_count       INTEGER      NOT NULL,
    imported_count  INTEGER      NOT NULL,
    duplicate_count INTEGER      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX import_batches_user_id_idx ON import_batches (user_id);
