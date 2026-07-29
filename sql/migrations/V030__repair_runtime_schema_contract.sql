-- Repair schema contracts that are used by the running services but were
-- missing from older manually-created databases and, for several tables,
-- from the repository baseline itself. All changes are additive so existing
-- legacy data remains available during forward adoption.

-- Identity login contract.
ALTER TABLE user_info
    ADD COLUMN IF NOT EXISTS last_login_time TIMESTAMP;

-- Governance operation-log contract. Backfill before enforcing the runtime
-- entity's non-null requirement.
ALTER TABLE operation_log
    ADD COLUMN IF NOT EXISTS operation_module VARCHAR(50);

UPDATE operation_log
SET operation_module = COALESCE(NULLIF(module, ''), NULLIF(operation_type, ''), 'unknown')
WHERE operation_module IS NULL;

ALTER TABLE operation_log
    ALTER COLUMN operation_module SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_operation_log_module
    ON operation_log(operation_module);

-- Billing budget contract used by the scheduled alert task.
CREATE TABLE IF NOT EXISTS tenant_budget (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    tenant_name VARCHAR(100),
    monthly_budget DECIMAL(18, 4) NOT NULL DEFAULT 0,
    used_amount DECIMAL(18, 4) NOT NULL DEFAULT 0,
    warning_threshold DECIMAL(8, 4) NOT NULL DEFAULT 0.8,
    limit_threshold DECIMAL(8, 4) NOT NULL DEFAULT 1.0,
    alert_level INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_budget_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_tenant_budget_tenant FOREIGN KEY (tenant_id) REFERENCES tenant_info(id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_budget_status
    ON tenant_budget(status);

-- Tables referenced by MyBatis entities but absent from the historical
-- baseline. They are deliberately created empty; services own their data.
CREATE TABLE IF NOT EXISTS encrypted_field (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(150) NOT NULL,
    field_name VARCHAR(150) NOT NULL,
    field_type VARCHAR(50),
    algorithm VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_encrypted_field UNIQUE (table_name, field_name)
);

CREATE TABLE IF NOT EXISTS masking_rule (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    table_name VARCHAR(150) NOT NULL,
    field_name VARCHAR(150) NOT NULL,
    field_type VARCHAR(50),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_masking_rule UNIQUE (tenant_id, table_name, field_name),
    CONSTRAINT fk_masking_rule_tenant FOREIGN KEY (tenant_id) REFERENCES tenant_info(id)
);

CREATE INDEX IF NOT EXISTS idx_masking_rule_lookup
    ON masking_rule(tenant_id, table_name, enabled);

CREATE TABLE IF NOT EXISTS config_version (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(200) NOT NULL,
    config_value TEXT,
    version_num BIGINT NOT NULL,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_config_version UNIQUE (config_key, version_num)
);

CREATE INDEX IF NOT EXISTS idx_config_version_history
    ON config_version(config_key, created_at DESC);

CREATE TABLE IF NOT EXISTS vendor_params_mapping (
    id BIGSERIAL PRIMARY KEY,
    vendor_config_id BIGINT NOT NULL,
    param_name VARCHAR(150) NOT NULL,
    param_type VARCHAR(50),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    default_value TEXT,
    transform_expr TEXT,
    validation_rule TEXT,
    source_field VARCHAR(200),
    target_field VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vendor_params_mapping_config
        FOREIGN KEY (vendor_config_id) REFERENCES vendor_config(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_vendor_params_mapping_config
    ON vendor_params_mapping(vendor_config_id);

-- Base access-domain tables were present in init.sql but were not created when
-- adopting an older database through changelogSync.
CREATE TABLE IF NOT EXISTS caller_product (
    id BIGSERIAL PRIMARY KEY,
    caller_id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    cache_scope VARCHAR(20) NOT NULL DEFAULT 'CALLER',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_caller_product_caller FOREIGN KEY (caller_id) REFERENCES caller_info(id),
    CONSTRAINT uk_caller_product UNIQUE (caller_id, product_code)
);

CREATE INDEX IF NOT EXISTS idx_caller_product_caller ON caller_product(caller_id);
CREATE INDEX IF NOT EXISTS idx_caller_product_code ON caller_product(product_code);
CREATE INDEX IF NOT EXISTS idx_caller_product_status ON caller_product(status);

CREATE TABLE IF NOT EXISTS api_key_product (
    id BIGSERIAL PRIMARY KEY,
    api_key_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT fk_apikey_product_key FOREIGN KEY (api_key_id) REFERENCES api_key(id),
    CONSTRAINT fk_apikey_product_product FOREIGN KEY (product_id) REFERENCES caller_product(id),
    CONSTRAINT uk_api_key_product UNIQUE (api_key_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_apikey_product_key ON api_key_product(api_key_id);
CREATE INDEX IF NOT EXISTS idx_apikey_product_product ON api_key_product(product_id);

CREATE TABLE IF NOT EXISTS call_scene (
    id BIGSERIAL PRIMARY KEY,
    scene_code VARCHAR(64) NOT NULL UNIQUE,
    scene_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_call_scene_code ON call_scene(scene_code);
CREATE INDEX IF NOT EXISTS idx_call_scene_status ON call_scene(status);

-- Add the current entity-facing columns alongside legacy names. Existing data
-- is copied where a direct mapping is available.
ALTER TABLE alert_rule
    ADD COLUMN IF NOT EXISTS metric_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS condition VARCHAR(20),
    ADD COLUMN IF NOT EXISTS threshold DECIMAL(10, 2),
    ADD COLUMN IF NOT EXISTS time_window INTEGER DEFAULT 300,
    ADD COLUMN IF NOT EXISTS severity VARCHAR(20) DEFAULT 'warning',
    ADD COLUMN IF NOT EXISTS notification_channels VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alert_rule' AND column_name = 'target_type') THEN
        EXECUTE 'UPDATE alert_rule SET metric_name = COALESCE(metric_name, target_type)';
        EXECUTE 'ALTER TABLE alert_rule ALTER COLUMN target_type DROP NOT NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alert_rule' AND column_name = 'condition_type') THEN
        EXECUTE 'UPDATE alert_rule SET condition = COALESCE(condition, condition_type)';
        EXECUTE 'ALTER TABLE alert_rule ALTER COLUMN condition_type DROP NOT NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alert_rule' AND column_name = 'threshold_value') THEN
        EXECUTE 'UPDATE alert_rule SET threshold = COALESCE(threshold, threshold_value)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alert_rule' AND column_name = 'time_window_minutes') THEN
        EXECUTE 'UPDATE alert_rule SET time_window = COALESCE(time_window, time_window_minutes, 300)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alert_rule' AND column_name = 'notify_channels') THEN
        EXECUTE 'UPDATE alert_rule SET notification_channels = COALESCE(notification_channels, notify_channels::TEXT)';
    END IF;
END $$;

UPDATE alert_rule
SET time_window = COALESCE(time_window, 300),
    severity = COALESCE(severity, 'warning');

ALTER TABLE alert_record
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT,
    ADD COLUMN IF NOT EXISTS alert_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS alert_title VARCHAR(200),
    ADD COLUMN IF NOT EXISTS alert_content TEXT,
    ADD COLUMN IF NOT EXISTS metric_value DECIMAL(10, 2),
    ADD COLUMN IF NOT EXISTS severity VARCHAR(20),
    ADD COLUMN IF NOT EXISTS fired_at TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alert_record' AND column_name = 'alert_message') THEN
        EXECUTE 'UPDATE alert_record SET alert_title = COALESCE(alert_title, alert_message), alert_content = COALESCE(alert_content, alert_message)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alert_record' AND column_name = 'triggered_value') THEN
        EXECUTE 'UPDATE alert_record SET metric_value = COALESCE(metric_value, triggered_value)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alert_record' AND column_name = 'alert_level') THEN
        EXECUTE 'UPDATE alert_record SET severity = COALESCE(severity, alert_level)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alert_record' AND column_name = 'alert_time') THEN
        EXECUTE 'UPDATE alert_record SET fired_at = COALESCE(fired_at, alert_time)';
    END IF;
END $$;

UPDATE alert_record
SET alert_type = COALESCE(alert_type, 'metric'),
    alert_title = COALESCE(alert_title, 'Alert'),
    severity = COALESCE(severity, 'warning'),
    fired_at = COALESCE(fired_at, created_at, CURRENT_TIMESTAMP);

-- Legacy billing_daily used different aggregate names. Keep both contracts and
-- backfill the current entity-facing columns.
ALTER TABLE billing_daily
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT,
    ADD COLUMN IF NOT EXISTS vendor_id BIGINT,
    ADD COLUMN IF NOT EXISTS data_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS call_count BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS success_count BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fail_count BIGINT DEFAULT 0;

UPDATE billing_daily billing
SET tenant_id = COALESCE(billing.tenant_id, caller.tenant_id)
FROM caller_info caller
WHERE caller.id = billing.caller_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'billing_daily' AND column_name = 'total_calls') THEN
        EXECUTE 'UPDATE billing_daily SET call_count = COALESCE(call_count, total_calls, 0)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'billing_daily' AND column_name = 'successful_calls') THEN
        EXECUTE 'UPDATE billing_daily SET success_count = COALESCE(success_count, successful_calls, 0)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'billing_daily' AND column_name = 'failed_calls') THEN
        EXECUTE 'UPDATE billing_daily SET fail_count = COALESCE(fail_count, failed_calls, 0)';
    END IF;
END $$;

-- Legacy call_record is partitioned by request_time. Retain that partitioning
-- and add the current model's columns; request_time already has a default, so
-- new inserts continue to route to the active monthly partition.
ALTER TABLE call_record
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT,
    ADD COLUMN IF NOT EXISTS api_key_id BIGINT,
    ADD COLUMN IF NOT EXISTS vendor_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS api_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS product_id BIGINT,
    ADD COLUMN IF NOT EXISTS product_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS product_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS scene_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS scene_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS data_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS data_type_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS request_params JSONB,
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS success BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS error_msg VARCHAR(500),
    ADD COLUMN IF NOT EXISTS latency INTEGER,
    ADD COLUMN IF NOT EXISTS duration_ms INTEGER,
    ADD COLUMN IF NOT EXISTS cached BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS use_cache BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cache_days INTEGER,
    ADD COLUMN IF NOT EXISTS cache_hit BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cache_scope VARCHAR(20) DEFAULT 'GLOBAL',
    ADD COLUMN IF NOT EXISTS cache_source_record_id BIGINT,
    ADD COLUMN IF NOT EXISTS response_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS call_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE call_record record
SET vendor_code = COALESCE(record.vendor_code, vendor.vendor_code)
FROM vendor_info vendor
WHERE vendor.id = record.vendor_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'call_record' AND column_name = 'data_type_id') THEN
        EXECUTE 'UPDATE call_record record SET data_type = COALESCE(record.data_type, data_type.data_type_code), data_type_code = COALESCE(record.data_type_code, data_type.data_type_code) FROM data_type data_type WHERE data_type.id = record.data_type_id';
        EXECUTE 'ALTER TABLE call_record ALTER COLUMN data_type_id DROP NOT NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'call_record' AND column_name = 'request_data') THEN
        EXECUTE 'UPDATE call_record SET request_params = COALESCE(request_params, request_data)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'call_record' AND column_name = 'status') THEN
        EXECUTE 'UPDATE call_record SET success = COALESCE(success, status = ''success'')';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'call_record' AND column_name = 'response_code') THEN
        EXECUTE 'UPDATE call_record SET error_code = COALESCE(error_code, response_code)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'call_record' AND column_name = 'error_message') THEN
        EXECUTE 'UPDATE call_record SET error_msg = COALESCE(error_msg, error_message)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'call_record' AND column_name = 'response_message') THEN
        EXECUTE 'UPDATE call_record SET error_msg = COALESCE(error_msg, response_message)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'call_record' AND column_name = 'response_time') THEN
        EXECUTE 'UPDATE call_record SET response_at = COALESCE(response_at, response_time)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'call_record' AND column_name = 'request_time') THEN
        EXECUTE 'UPDATE call_record SET call_time = COALESCE(call_time, request_time)';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_call_record_trace ON call_record(trace_id);
CREATE INDEX IF NOT EXISTS idx_call_record_cache_lookup
    ON call_record(api_code, request_hash, request_time);
CREATE INDEX IF NOT EXISTS idx_call_record_product ON call_record(product_code);
CREATE INDEX IF NOT EXISTS idx_call_record_scene ON call_record(scene_code);
CREATE INDEX IF NOT EXISTS idx_call_record_cache_hit ON call_record(cache_hit);
