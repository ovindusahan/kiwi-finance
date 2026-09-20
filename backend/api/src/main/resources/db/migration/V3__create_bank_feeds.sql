CREATE TABLE bank_connections (
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider           VARCHAR(20)  NOT NULL CHECK (provider IN ('AKAHU')),
    method             VARCHAR(20)  NOT NULL CHECK (method IN ('PERSONAL_APP', 'OAUTH')),
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'REAUTH_REQUIRED', 'DISCONNECTED')),
    credentials        BYTEA,
    credentials_key_id VARCHAR(40),
    external_user_id   VARCHAR(100) NOT NULL,
    last_synced_at     TIMESTAMPTZ,
    sync_locked_until  TIMESTAMPTZ,
    disconnected_at    TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT bank_connections_credentials CHECK ((status = 'DISCONNECTED') = (credentials IS NULL)),
    CONSTRAINT bank_connections_credentials_key CHECK ((credentials IS NULL) = (credentials_key_id IS NULL))
);

CREATE INDEX bank_connections_user_id_idx ON bank_connections (user_id);
CREATE UNIQUE INDEX bank_connections_user_provider_key ON bank_connections (user_id, provider) WHERE status <> 'DISCONNECTED';

CREATE TABLE bank_feed_accounts (
    id                          UUID PRIMARY KEY,
    connection_id               UUID         NOT NULL REFERENCES bank_connections (id) ON DELETE CASCADE,
    user_id                     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    external_id                 VARCHAR(100) NOT NULL,
    name                        VARCHAR(200) NOT NULL,
    institution                 VARCHAR(200),
    account_type                VARCHAR(20)  NOT NULL
        CHECK (account_type IN ('EVERYDAY', 'SAVINGS', 'CREDIT_CARD', 'LOAN', 'KIWISAVER', 'INVESTMENT', 'CASH', 'OTHER')),
    masked_number               VARCHAR(40),
    balance_cents               BIGINT,
    balance_updated_at          TIMESTAMPTZ,
    supports_transactions       BOOLEAN      NOT NULL,
    is_active                   BOOLEAN      NOT NULL,
    account_id                  UUID REFERENCES accounts (id) ON DELETE SET NULL,
    sync_enabled                BOOLEAN      NOT NULL DEFAULT FALSE,
    transactions_synced_through TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ  NOT NULL,
    updated_at                  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT bank_feed_accounts_external_key UNIQUE (connection_id, external_id)
);

CREATE INDEX bank_feed_accounts_user_id_idx ON bank_feed_accounts (user_id);
CREATE INDEX bank_feed_accounts_account_id_idx ON bank_feed_accounts (account_id);

CREATE TABLE bank_sync_runs (
    id                   UUID PRIMARY KEY,
    connection_id        UUID        NOT NULL REFERENCES bank_connections (id) ON DELETE CASCADE,
    user_id              UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    trigger_type         VARCHAR(20) NOT NULL CHECK (trigger_type IN ('INITIAL', 'MANUAL', 'SCHEDULED')),
    status               VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    started_at           TIMESTAMPTZ NOT NULL,
    finished_at          TIMESTAMPTZ,
    accounts_synced      INTEGER     NOT NULL DEFAULT 0,
    transactions_created INTEGER     NOT NULL DEFAULT 0,
    transactions_updated INTEGER     NOT NULL DEFAULT 0,
    error_code           VARCHAR(60),
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX bank_sync_runs_connection_started_idx ON bank_sync_runs (connection_id, started_at DESC);

CREATE TABLE oauth_states (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider    VARCHAR(20) NOT NULL CHECK (provider IN ('AKAHU')),
    state_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX oauth_states_state_hash_key ON oauth_states (state_hash);
