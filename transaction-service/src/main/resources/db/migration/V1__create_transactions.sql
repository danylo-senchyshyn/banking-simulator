CREATE TABLE transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(64)    NOT NULL UNIQUE,
    sender_id       UUID           NOT NULL,
    from_account_id UUID           NOT NULL,
    to_account_id   UUID           NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_sender ON transactions (sender_id);
CREATE INDEX idx_transactions_from_account ON transactions (from_account_id);
CREATE INDEX idx_transactions_to_account ON transactions (to_account_id);
