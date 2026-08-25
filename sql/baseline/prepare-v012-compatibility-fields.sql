-- Some pre-Liquibase databases already use the newer quality/API-key shape.
-- Recreate only the compatibility columns expected by V012/V016 so the
-- published migration files remain immutable.
ALTER TABLE quality_rule
    ADD COLUMN IF NOT EXISTS rule_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS target_table VARCHAR(100);

ALTER TABLE quality_score
    ADD COLUMN IF NOT EXISTS rule_id BIGINT,
    ADD COLUMN IF NOT EXISTS score_date DATE,
    ADD COLUMN IF NOT EXISTS score_value DECIMAL(5, 2);

ALTER TABLE api_key
    ADD COLUMN IF NOT EXISTS api_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS api_secret VARCHAR(128),
    ADD COLUMN IF NOT EXISTS rate_limit INTEGER NOT NULL DEFAULT 100,
    ADD COLUMN IF NOT EXISTS quota_limit BIGINT NOT NULL DEFAULT 100000,
    ADD COLUMN IF NOT EXISTS quota_used BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS expire_time TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'api_key' AND column_name = 'key_value'
    ) THEN
        UPDATE api_key
        SET api_key = key_value
        WHERE api_key IS NULL;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'api_key' AND column_name = 'expires_at'
    ) THEN
        UPDATE api_key
        SET expire_time = expires_at
        WHERE expire_time IS NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_api_key ON api_key(api_key);
