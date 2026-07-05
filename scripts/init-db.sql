-- Settlement layer schema. Runs automatically via docker-entrypoint-initdb.d.
CREATE TABLE settlement_accounts (
    participant_id VARCHAR(32) PRIMARY KEY,
    balance NUMERIC(19, 4) NOT NULL,
    opening_balance NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CAD'
);
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    participant_id VARCHAR(32) NOT NULL REFERENCES settlement_accounts(participant_id),
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_message ON ledger_entries (message_id);
CREATE INDEX idx_ledger_participant ON ledger_entries (participant_id);
CREATE TABLE processed_messages (
    message_id VARCHAR(64) PRIMARY KEY,
    debtor_participant VARCHAR(32) NOT NULL,
    creditor_participant VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_processed_debtor_time ON processed_messages (debtor_participant, created_at);
-- Seed participant settlement accounts (think: direct clearers holding
-- prefunded liquidity at the central bank).
INSERT INTO settlement_accounts (
        participant_id,
        balance,
        opening_balance,
        currency
    )
VALUES ('ALPHA_BANK', 1000000.0000, 1000000.0000, 'CAD'),
    ('BETA_BANK', 1000000.0000, 1000000.0000, 'CAD'),
    ('GAMMA_CU', 500000.0000, 500000.0000, 'CAD'),
    ('DELTA_FIN', 250000.0000, 250000.0000, 'CAD');