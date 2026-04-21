CREATE TABLE accounts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL,
    currency   VARCHAR(3)   NOT NULL,
    status     VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    balance    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);
