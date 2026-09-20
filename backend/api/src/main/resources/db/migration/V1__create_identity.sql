CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    email              VARCHAR(320) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    display_name       VARCHAR(100) NOT NULL,
    failed_login_count INTEGER      NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT users_email_lowercase CHECK (email = lower(email))
);

CREATE UNIQUE INDEX users_email_key ON users (email);

CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id      UUID        NOT NULL,
    token_hash     VARCHAR(64) NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id UUID REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX refresh_tokens_token_hash_key ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_id_idx ON refresh_tokens (family_id);

CREATE TABLE user_profiles (
    id                      UUID PRIMARY KEY,
    user_id                 UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date_of_birth           DATE,
    region                  VARCHAR(30),
    household_size          INTEGER      NOT NULL DEFAULT 1 CHECK (household_size BETWEEN 1 AND 20),
    dependants              INTEGER      NOT NULL DEFAULT 0 CHECK (dependants BETWEEN 0 AND 20),
    employment_type         VARCHAR(20)  NOT NULL DEFAULT 'EMPLOYEE'
        CHECK (employment_type IN ('EMPLOYEE', 'SELF_EMPLOYED', 'CONTRACTOR', 'STUDENT', 'NOT_WORKING', 'RETIRED')),
    housing_type            VARCHAR(20)
        CHECK (housing_type IN ('RENTING', 'MORTGAGE', 'OWN_OUTRIGHT', 'BOARDING', 'WITH_FAMILY')),
    single_income_household BOOLEAN      NOT NULL DEFAULT FALSE,
    tax_code                VARCHAR(4)   NOT NULL DEFAULT 'M'
        CHECK (tax_code IN ('M', 'ME', 'SB', 'S', 'SH', 'ST', 'SA')),
    has_student_loan        BOOLEAN      NOT NULL DEFAULT FALSE,
    kiwisaver_member        BOOLEAN      NOT NULL DEFAULT FALSE,
    kiwisaver_rate          NUMERIC(5, 4),
    kiwisaver_joined_on     DATE,
    kiwisaver_balance_cents BIGINT,
    first_home_buyer        BOOLEAN      NOT NULL DEFAULT FALSE,
    pay_frequency           VARCHAR(20)  NOT NULL DEFAULT 'FORTNIGHTLY'
        CHECK (pay_frequency IN ('WEEKLY', 'FORTNIGHTLY', 'FOUR_WEEKLY', 'MONTHLY')),
    savings_interest_rate   NUMERIC(6, 5) NOT NULL DEFAULT 0.025 CHECK (savings_interest_rate BETWEEN 0 AND 0.2),
    target_savings_rate     NUMERIC(5, 4) NOT NULL DEFAULT 0.15 CHECK (target_savings_rate BETWEEN 0 AND 0.8),
    onboarded_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT user_profiles_kiwisaver_rate CHECK (kiwisaver_member OR kiwisaver_rate IS NULL)
);

CREATE UNIQUE INDEX user_profiles_user_id_key ON user_profiles (user_id);
