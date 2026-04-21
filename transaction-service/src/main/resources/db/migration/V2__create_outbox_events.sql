CREATE TABLE outbox_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic      VARCHAR(255) NOT NULL,
    key        VARCHAR(255),
    payload    TEXT         NOT NULL,
    sent       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    sent_at    TIMESTAMP
);

CREATE INDEX idx_outbox_events_sent_created ON outbox_events (sent, created_at)
    WHERE sent = FALSE;
