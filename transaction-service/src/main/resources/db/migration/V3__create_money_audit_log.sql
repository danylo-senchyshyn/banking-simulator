CREATE TABLE money_audit_log (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    action           VARCHAR(50)  NOT NULL,
    transaction_id   UUID,
    from_account_id  UUID,
    to_account_id    UUID,
    amount           NUMERIC(19, 4),
    currency         VARCHAR(10),
    details          TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_money_audit_log_user_id    ON money_audit_log (user_id);
CREATE INDEX idx_money_audit_log_created_at ON money_audit_log (created_at);
