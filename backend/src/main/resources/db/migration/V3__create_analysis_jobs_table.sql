CREATE TABLE analysis_jobs (
    job_id          VARCHAR(36) PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    request_payload JSONB,
    result_payload  JSONB,
    error_message   VARCHAR(1000),
    latency_ms      BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_analysis_jobs_user_id ON analysis_jobs(user_id);
CREATE INDEX idx_analysis_jobs_status ON analysis_jobs(status);
CREATE INDEX idx_analysis_jobs_created_at ON analysis_jobs(created_at DESC);
