CREATE TABLE transactions (
    id VARCHAR(64) PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    account_id VARCHAR(128) NOT NULL,
    card_fingerprint VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    merchant VARCHAR(255) NOT NULL,
    merchant_category VARCHAR(128) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    geo_country VARCHAR(2) NOT NULL,
    billing_country VARCHAR(2) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    decision VARCHAR(32),
    score DOUBLE PRECISION,
    scored_at TIMESTAMPTZ,
    degraded BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_transactions_account_created_at
    ON transactions (account_id, created_at);

CREATE INDEX idx_transactions_account_device
    ON transactions (account_id, device_id);
