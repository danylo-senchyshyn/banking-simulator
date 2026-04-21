CREATE TABLE cards (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id        UUID          NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    card_number       VARCHAR(19)   NOT NULL UNIQUE,
    status            VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    transaction_limit NUMERIC(19, 4),
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL
);

CREATE INDEX idx_cards_account_id ON cards (account_id);
