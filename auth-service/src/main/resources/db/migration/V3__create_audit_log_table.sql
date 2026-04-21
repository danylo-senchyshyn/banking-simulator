CREATE TABLE audit_log (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID      REFERENCES users (id) ON DELETE SET NULL,
    action     VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    details    TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_user_id    ON audit_log (user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
