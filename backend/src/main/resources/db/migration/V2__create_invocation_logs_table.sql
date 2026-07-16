CREATE TABLE invocation_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    endpoint        VARCHAR(100) NOT NULL,
    request_body    JSONB,
    response_body   JSONB,
    status_code     INT,
    latency_ms      BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invocation_logs_user_id ON invocation_logs(user_id);
CREATE INDEX idx_invocation_logs_created_at ON invocation_logs(created_at DESC);
