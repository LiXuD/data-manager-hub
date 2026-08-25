-- Stage-4 vendor-by-vendor migration control plane.
-- Masterdata owns migration intent and gates; Access and Billing expose only aggregate facts.

ALTER TABLE billing_event
    ADD COLUMN IF NOT EXISTS plugin_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS plugin_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pipeline_version INTEGER,
    ADD COLUMN IF NOT EXISTS snapshot_hash CHAR(64);

CREATE INDEX IF NOT EXISTS idx_billing_event_connector_trace
    ON billing_event(vendor_id, interface_id, pipeline_version, snapshot_hash, call_time)
    WHERE pipeline_version IS NOT NULL AND snapshot_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_call_record_connector_migration_trace
    ON call_record(vendor_id, pipeline_version, snapshot_hash, call_time)
    WHERE pipeline_version IS NOT NULL AND snapshot_hash IS NOT NULL;

CREATE TABLE IF NOT EXISTS vendor_connector_migration (
    id BIGSERIAL PRIMARY KEY,
    vendor_config_id BIGINT NOT NULL UNIQUE REFERENCES vendor_config(id),
    vendor_id BIGINT NOT NULL REFERENCES vendor_info(id),
    interface_id BIGINT NOT NULL REFERENCES api_interface(id),
    state VARCHAR(32) NOT NULL
        CHECK (state IN ('PREPARED', 'VALIDATED', 'TEST_PASSED', 'OBSERVING', 'READY',
                         'STABLE', 'FAILED', 'BLOCKED', 'ROLLED_BACK')),
    record_version INTEGER NOT NULL DEFAULT 0 CHECK (record_version >= 0),
    source_config_hash CHAR(64),
    draft_id BIGINT REFERENCES vendor_connector_version(id),
    draft_version INTEGER,
    draft_snapshot_hash CHAR(64),
    published_connector_version_id BIGINT REFERENCES vendor_connector_version(id),
    published_version_no INTEGER,
    previous_runtime_mode VARCHAR(16) NOT NULL DEFAULT 'LEGACY'
        CHECK (previous_runtime_mode IN ('LEGACY', 'PLUGIN')),
    previous_active_connector_version_id BIGINT REFERENCES vendor_connector_version(id),
    previous_connector_version INTEGER NOT NULL DEFAULT 0,
    minimum_observation_minutes INTEGER NOT NULL DEFAULT 60
        CHECK (minimum_observation_minutes BETWEEN 0 AND 10080),
    minimum_calls BIGINT NOT NULL DEFAULT 100 CHECK (minimum_calls BETWEEN 1 AND 1000000),
    maximum_error_rate DOUBLE PRECISION NOT NULL DEFAULT 0.05
        CHECK (maximum_error_rate BETWEEN 0 AND 1),
    maximum_p95_duration_ms BIGINT NOT NULL DEFAULT 5000
        CHECK (maximum_p95_duration_ms BETWEEN 1 AND 600000),
    minimum_billing_coverage_rate DOUBLE PRECISION NOT NULL DEFAULT 1
        CHECK (minimum_billing_coverage_rate BETWEEN 0 AND 1),
    observation_started_at TIMESTAMP,
    observation_eligible_at TIMESTAMP,
    observed_calls BIGINT NOT NULL DEFAULT 0,
    observed_successes BIGINT NOT NULL DEFAULT 0,
    observed_failures BIGINT NOT NULL DEFAULT 0,
    observed_error_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    observed_p95_duration_ms BIGINT NOT NULL DEFAULT 0,
    observed_cache_hits BIGINT NOT NULL DEFAULT 0,
    observed_realtime_calls BIGINT NOT NULL DEFAULT 0,
    observed_billing_events BIGINT NOT NULL DEFAULT 0,
    observed_posted_billing_events BIGINT NOT NULL DEFAULT 0,
    observed_billing_coverage_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    observed_billing_amount DECIMAL(20, 6) NOT NULL DEFAULT 0,
    observation_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    safe_error_code VARCHAR(64),
    safe_error_digest CHAR(64),
    completed_at TIMESTAMP,
    rolled_back_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (source_config_hash IS NULL OR source_config_hash ~ '^[0-9a-f]{64}$'),
    CHECK (draft_snapshot_hash IS NULL OR draft_snapshot_hash ~ '^[0-9a-f]{64}$'),
    CHECK (safe_error_digest IS NULL OR safe_error_digest ~ '^[0-9a-f]{64}$'),
    CHECK (observed_calls >= 0 AND observed_successes >= 0 AND observed_failures >= 0),
    CHECK (observed_error_rate BETWEEN 0 AND 1),
    CHECK (observed_billing_coverage_rate BETWEEN 0 AND 1),
    CHECK (state NOT IN ('OBSERVING', 'READY', 'STABLE') OR
           (published_connector_version_id IS NOT NULL AND published_version_no IS NOT NULL AND
            observation_started_at IS NOT NULL AND observation_eligible_at IS NOT NULL)),
    CHECK (state <> 'STABLE' OR (observation_gate_passed AND completed_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_vendor_connector_migration_state
    ON vendor_connector_migration(state, updated_at);
CREATE INDEX IF NOT EXISTS idx_vendor_connector_migration_observation
    ON vendor_connector_migration(observation_eligible_at)
    WHERE state IN ('OBSERVING', 'READY');

INSERT INTO permission (
    permission_code, permission_name, resource_type, resource_path,
    parent_id, sort_order, description, status, deleted
)
VALUES (
    'connector-plugin:migrate', '连接器插件-迁移', 'button', '/vendor/connector-migration',
    0, 79, '批量准备并逐厂商推进legacy-http连接器迁移及稳定观察', 'active', FALSE
)
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    resource_type = EXCLUDED.resource_type,
    resource_path = EXCLUDED.resource_path,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role_info.id, permission.id, CURRENT_TIMESTAMP
FROM role_info
JOIN permission ON permission.permission_code = 'connector-plugin:migrate'
WHERE LOWER(role_info.role_code) = 'admin'
  AND role_info.status = 'active'
  AND role_info.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;

COMMENT ON TABLE vendor_connector_migration IS
    'Masterdata-owned vendor-by-vendor connector migration state and aggregate observation gate';
COMMENT ON COLUMN vendor_connector_migration.source_config_hash IS
    'Hash of source vendor fields and security references; no plaintext secret is persisted';
COMMENT ON COLUMN vendor_connector_migration.safe_error_digest IS
    'SHA-256 digest of a safe migration error; request, response and secret values are never persisted';
