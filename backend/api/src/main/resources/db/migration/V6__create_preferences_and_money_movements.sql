-- One emergency fund account per person. Where several were marked, keep the open one with the
-- largest balance.
UPDATE accounts a
SET include_in_emergency_fund = FALSE
WHERE include_in_emergency_fund
  AND (a.archived_at IS NOT NULL
       OR a.id <> (SELECT b.id
                   FROM accounts b
                   WHERE b.user_id = a.user_id AND b.include_in_emergency_fund AND b.archived_at IS NULL
                   ORDER BY b.current_balance_cents DESC, b.created_at
                   LIMIT 1));

CREATE UNIQUE INDEX accounts_one_emergency_fund_per_user
    ON accounts (user_id) WHERE include_in_emergency_fund;

-- Settings that shape the app rather than the person's finances.
CREATE TABLE user_preferences (
    id                                UUID PRIMARY KEY,
    user_id                           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    home_layout                       TEXT,
    emergency_fund_reminders          BOOLEAN     NOT NULL DEFAULT TRUE,
    emergency_fund_reminder_snoozed_until DATE,
    created_at                        TIMESTAMPTZ NOT NULL,
    updated_at                        TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX user_preferences_user_id_key ON user_preferences (user_id);

-- Transfers, withdrawals and deposits recorded in Kiwi Finance. Each creates one transaction per
-- account it touches. On bank-fed accounts the recorded transaction stands in until the bank's own
-- copy arrives, and is then removed.
CREATE TABLE money_movements (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    movement_type   VARCHAR(12)  NOT NULL CHECK (movement_type IN ('TRANSFER', 'WITHDRAWAL', 'DEPOSIT')),
    from_account_id UUID REFERENCES accounts (id) ON DELETE SET NULL,
    to_account_id   UUID REFERENCES accounts (id) ON DELETE SET NULL,
    amount_cents    BIGINT       NOT NULL CHECK (amount_cents > 0),
    moved_on        DATE         NOT NULL,
    note            VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX money_movements_user_id_idx ON money_movements (user_id, moved_on DESC);

ALTER TABLE transactions
    ADD COLUMN movement_id UUID REFERENCES money_movements (id) ON DELETE SET NULL,
    ADD COLUMN awaiting_bank_copy BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX transactions_awaiting_bank_copy_idx ON transactions (account_id) WHERE awaiting_bank_copy;
