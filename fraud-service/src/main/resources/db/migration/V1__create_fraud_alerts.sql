CREATE TABLE fraud_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID        NOT NULL,
    sender_id       UUID        NOT NULL,
    from_account_id UUID        NOT NULL,
    to_account_id   UUID        NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        VARCHAR(3)  NOT NULL,
    approved        BOOLEAN     NOT NULL,
    reason          VARCHAR(255),
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_alerts_transaction_id ON fraud_alerts (transaction_id);
CREATE INDEX idx_fraud_alerts_sender_id ON fraud_alerts (sender_id);
