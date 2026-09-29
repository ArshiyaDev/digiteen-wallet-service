CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY,
    requester_user_id UUID NOT NULL,
    source_wallet_id UUID REFERENCES wallets(id),
    target_wallet_id UUID REFERENCES wallets(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED', 'REJECTED')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    source_balance_after BIGINT,
    target_balance_after BIGINT,
    failure_code VARCHAR(50),
    trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX wallet_transactions_source_idx ON wallet_transactions(source_wallet_id, created_at DESC);
CREATE INDEX wallet_transactions_target_idx ON wallet_transactions(target_wallet_id, created_at DESC);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    transaction_id UUID REFERENCES wallet_transactions(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT idempotency_user_key_unique UNIQUE (user_id, idempotency_key)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000)
);
CREATE INDEX outbox_events_unpublished_idx ON outbox_events(occurred_at) WHERE published_at IS NULL;

CREATE TABLE consumed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL
);
