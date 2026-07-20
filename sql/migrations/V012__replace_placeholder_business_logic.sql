-- Align data-quality persistence with the implemented quality API.
ALTER TABLE quality_rule ADD COLUMN IF NOT EXISTS data_type VARCHAR(100);
ALTER TABLE quality_rule ADD COLUMN IF NOT EXISTS check_expression TEXT;
ALTER TABLE quality_rule ADD COLUMN IF NOT EXISTS severity INTEGER;
ALTER TABLE quality_rule ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE quality_rule ALTER COLUMN rule_code DROP NOT NULL;
ALTER TABLE quality_rule ALTER COLUMN target_table DROP NOT NULL;

ALTER TABLE quality_score ADD COLUMN IF NOT EXISTS data_type VARCHAR(100);
ALTER TABLE quality_score ADD COLUMN IF NOT EXISTS data_id BIGINT;
ALTER TABLE quality_score ADD COLUMN IF NOT EXISTS score DOUBLE PRECISION;
ALTER TABLE quality_score ADD COLUMN IF NOT EXISTS issue_summary TEXT;
ALTER TABLE quality_score ADD COLUMN IF NOT EXISTS checked_at TIMESTAMP;
ALTER TABLE quality_score ALTER COLUMN rule_id DROP NOT NULL;
ALTER TABLE quality_score ALTER COLUMN score_date DROP NOT NULL;
ALTER TABLE quality_score ALTER COLUMN score_value DROP NOT NULL;
ALTER TABLE quality_score ALTER COLUMN pass_count TYPE INTEGER USING pass_count::INTEGER;
ALTER TABLE quality_score ALTER COLUMN fail_count TYPE INTEGER USING fail_count::INTEGER;

-- Preserve the operator's resolution instead of discarding it.
ALTER TABLE alert_record ADD COLUMN IF NOT EXISTS resolution TEXT;

CREATE TABLE IF NOT EXISTS service_health_check (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(150) NOT NULL,
    healthy BOOLEAN NOT NULL,
    response_time BIGINT NOT NULL,
    instance_count INTEGER NOT NULL,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_service_health_check_lookup
    ON service_health_check(service_name, checked_at DESC);

CREATE TABLE IF NOT EXISTS encryption_key (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(150) NOT NULL,
    encrypted_key TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_encryption_key_active
    ON encryption_key(table_name) WHERE active = TRUE;

-- Disable fixtures created by the former V009 demo migration on existing databases.
UPDATE vendor_config
SET status = 'inactive', updated_at = CURRENT_TIMESTAMP
WHERE api_url LIKE 'mock://%';
UPDATE vendor_info
SET status = 'inactive', updated_at = CURRENT_TIMESTAMP
WHERE vendor_code = 'mock_vendor';
UPDATE api_key
SET status = 'inactive', updated_at = CURRENT_TIMESTAMP
WHERE api_key = 'dp_demo_openapi_key';
