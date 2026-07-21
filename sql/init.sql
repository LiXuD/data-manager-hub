-- =====================================================
-- 数据管理平台 DDL脚本
-- 数据库: PostgreSQL 16
-- 创建时间: 2026-04-19
-- =====================================================

-- 1. 租户信息表
CREATE TABLE IF NOT EXISTS tenant_info (
    id BIGSERIAL PRIMARY KEY,
    tenant_code VARCHAR(50) NOT NULL UNIQUE,
    tenant_name VARCHAR(100) NOT NULL,
    tenant_type VARCHAR(20) NOT NULL DEFAULT 'enterprise',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(100),
    max_api_keys INTEGER DEFAULT 10,
    max_callers INTEGER DEFAULT 50,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_tenant_code ON tenant_info(tenant_code);
CREATE INDEX idx_tenant_status ON tenant_info(status);

-- 2. 厂商信息表
CREATE TABLE IF NOT EXISTS vendor_info (
    id BIGSERIAL PRIMARY KEY,
    vendor_code VARCHAR(50) NOT NULL UNIQUE,
    vendor_name VARCHAR(100) NOT NULL,
    vendor_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    description VARCHAR(500),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(100),
    secret_key VARCHAR(128),
    contract_start DATE,
    contract_end DATE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_vendor_code ON vendor_info(vendor_code);
CREATE INDEX idx_vendor_status ON vendor_info(status);

-- 3. 数据类型表
CREATE TABLE IF NOT EXISTS data_type (
    id BIGSERIAL PRIMARY KEY,
    data_type_code VARCHAR(50) NOT NULL UNIQUE,
    data_type_name VARCHAR(100) NOT NULL,
    data_category VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    pricing_model VARCHAR(20) NOT NULL DEFAULT 'per_call',
    unit_price DECIMAL(10, 4) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_datatype_code ON data_type(data_type_code);

-- 4. 厂商配置表
CREATE TABLE IF NOT EXISTS vendor_config (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL,
    data_type_id BIGINT NOT NULL,
    data_type_code VARCHAR(50),  -- 数据类型编码(冗余字段)
    api_url VARCHAR(500) NOT NULL,
    method VARCHAR(10) NOT NULL DEFAULT 'POST',
    timeout INTEGER NOT NULL DEFAULT 5000,
    retry_count INTEGER NOT NULL DEFAULT 3,
    circuit_threshold INTEGER NOT NULL DEFAULT 10,
    circuit_timeout INTEGER NOT NULL DEFAULT 30000,
    sign_type VARCHAR(20),
    encrypt_type VARCHAR(20),
    header_config JSONB,
    request_template JSONB,
    response_mapping JSONB,
    fallback_vendor_id BIGINT,
    security_version INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_vendor_config_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_info(id),
    CONSTRAINT fk_vendor_config_datatype FOREIGN KEY (data_type_id) REFERENCES data_type(id),
    UNIQUE(vendor_id, data_type_id)
);

CREATE INDEX idx_vendor_config_vendor ON vendor_config(vendor_id);
CREATE INDEX idx_vendor_config_datatype ON vendor_config(data_type_id);
CREATE INDEX IF NOT EXISTS idx_vendor_config_data_type_code ON vendor_config(data_type_code);

CREATE TABLE IF NOT EXISTS vendor_interface_security_step (
    id BIGSERIAL PRIMARY KEY,
    vendor_config_id BIGINT NOT NULL REFERENCES vendor_config(id) ON DELETE CASCADE,
    step_key VARCHAR(100) NOT NULL,
    direction VARCHAR(16) NOT NULL CHECK (direction IN ('REQUEST', 'RESPONSE')),
    step_type VARCHAR(32) NOT NULL,
    step_name VARCHAR(100),
    sort_no INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(vendor_config_id, direction, step_key),
    UNIQUE(vendor_config_id, direction, sort_no)
);

CREATE TABLE IF NOT EXISTS vendor_interface_security_version (
    id BIGSERIAL PRIMARY KEY,
    vendor_config_id BIGINT NOT NULL REFERENCES vendor_config(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    config_snapshot JSONB NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(vendor_config_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_vendor_security_step_config
    ON vendor_interface_security_step(vendor_config_id, direction, sort_no);
CREATE INDEX IF NOT EXISTS idx_vendor_security_version_config
    ON vendor_interface_security_version(vendor_config_id, version_no DESC);

-- 5. 调用方信息表
CREATE TABLE IF NOT EXISTS caller_info (
    id BIGSERIAL PRIMARY KEY,
    caller_code VARCHAR(50) NOT NULL UNIQUE,
    caller_name VARCHAR(100) NOT NULL,
    tenant_id BIGINT,
    caller_type VARCHAR(20) NOT NULL DEFAULT 'system',
    description VARCHAR(500),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_caller_tenant FOREIGN KEY (tenant_id) REFERENCES tenant_info(id)
);

CREATE INDEX idx_caller_code ON caller_info(caller_code);
CREATE INDEX idx_caller_tenant ON caller_info(tenant_id);

CREATE TABLE IF NOT EXISTS caller_product (
    id BIGSERIAL PRIMARY KEY,
    caller_id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    cache_scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_caller_product_caller FOREIGN KEY (caller_id) REFERENCES caller_info(id),
    UNIQUE(caller_id, product_code)
);

CREATE INDEX idx_caller_product_caller ON caller_product(caller_id);
CREATE INDEX idx_caller_product_code ON caller_product(product_code);
CREATE INDEX idx_caller_product_status ON caller_product(status);

-- 6. API Key表
CREATE TABLE IF NOT EXISTS api_key (
    id BIGSERIAL PRIMARY KEY,
    caller_id BIGINT NOT NULL,
    key_name VARCHAR(100),
    api_key VARCHAR(64) NOT NULL UNIQUE,
    api_secret VARCHAR(128) NOT NULL,
    rate_limit_enabled BOOLEAN NOT NULL DEFAULT true,
    rate_limit INTEGER NOT NULL DEFAULT 100,
    quota_limit BIGINT NOT NULL DEFAULT 100000,
    quota_used BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    expire_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_apikey_caller FOREIGN KEY (caller_id) REFERENCES caller_info(id)
);

CREATE INDEX idx_api_key ON api_key(api_key);
CREATE INDEX idx_apikey_caller ON api_key(caller_id);
CREATE INDEX IF NOT EXISTS idx_apikey_key_name ON api_key(key_name);

CREATE TABLE IF NOT EXISTS api_key_product (
    id BIGSERIAL PRIMARY KEY,
    api_key_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT fk_apikey_product_key FOREIGN KEY (api_key_id) REFERENCES api_key(id),
    CONSTRAINT fk_apikey_product_product FOREIGN KEY (product_id) REFERENCES caller_product(id),
    UNIQUE(api_key_id, product_id)
);

CREATE INDEX idx_apikey_product_key ON api_key_product(api_key_id);
CREATE INDEX idx_apikey_product_product ON api_key_product(product_id);

CREATE TABLE IF NOT EXISTS call_scene (
    id BIGSERIAL PRIMARY KEY,
    scene_code VARCHAR(64) NOT NULL UNIQUE,
    scene_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_call_scene_code ON call_scene(scene_code);
CREATE INDEX idx_call_scene_status ON call_scene(status);

-- 7. 调用记录表 (分区表)
CREATE TABLE IF NOT EXISTS call_record (
    id BIGSERIAL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(128),
    tenant_id BIGINT NOT NULL,
    caller_id BIGINT NOT NULL,
    api_key_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    vendor_code VARCHAR(50) NOT NULL,
    api_code VARCHAR(64),
    product_id BIGINT,
    product_code VARCHAR(64),
    product_name VARCHAR(100),
    scene_code VARCHAR(64),
    scene_name VARCHAR(100),
    data_type VARCHAR(50) NOT NULL,
    data_type_code VARCHAR(50),
    request_params JSONB,
    request_hash VARCHAR(64),
    response_data JSONB,
    success BOOLEAN NOT NULL DEFAULT true,
    error_code VARCHAR(20),
    error_msg VARCHAR(500),
    latency INTEGER,
    duration_ms INTEGER,
    cost DECIMAL(10, 4),
    cached BOOLEAN NOT NULL DEFAULT false,
    use_cache BOOLEAN NOT NULL DEFAULT false,
    cache_days INTEGER,
    cache_hit BOOLEAN NOT NULL DEFAULT false,
    cache_scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
    cache_source_record_id BIGINT,
    request_time TIMESTAMP,
    response_at TIMESTAMP,
    call_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, call_time)
) PARTITION BY RANGE (call_time);

ALTER TABLE call_record ADD COLUMN IF NOT EXISTS api_code VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS trace_id VARCHAR(128);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS product_id BIGINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS product_code VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS product_name VARCHAR(100);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS scene_code VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS scene_name VARCHAR(100);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS data_type_code VARCHAR(50);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS duration_ms INTEGER;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS use_cache BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS cache_days INTEGER;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS cache_hit BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS cache_scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL';
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS cache_source_record_id BIGINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS request_time TIMESTAMP;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS response_at TIMESTAMP;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS response_contract_valid BOOLEAN;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS response_contract_errors JSONB;

-- 分区策略: 按月分区，初始化当前月和下月，避免时间推进后写入失败
CREATE OR REPLACE FUNCTION create_monthly_partition(partition_date DATE)
RETURNS VOID AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    start_date := DATE_TRUNC('month', partition_date);
    end_date := start_date + INTERVAL '1 month';
    partition_name := 'call_record_' || TO_CHAR(start_date, 'YYYY_MM');

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF call_record FOR VALUES FROM (%L) TO (%L)',
        partition_name, start_date, end_date
    );
END;
$$ LANGUAGE plpgsql;

SELECT create_monthly_partition(CURRENT_DATE::DATE);
SELECT create_monthly_partition((CURRENT_DATE + INTERVAL '1 month')::DATE);

-- 索引
CREATE INDEX idx_call_record_request ON call_record(request_id);
CREATE INDEX idx_call_record_trace ON call_record(trace_id);
CREATE INDEX idx_call_record_tenant ON call_record(tenant_id);
CREATE INDEX idx_call_record_caller ON call_record(caller_id);
CREATE INDEX idx_call_record_vendor ON call_record(vendor_id);
CREATE INDEX idx_call_record_time ON call_record(call_time);
CREATE INDEX idx_call_record_cache_lookup ON call_record(api_code, request_hash, call_time);
CREATE INDEX idx_call_record_product ON call_record(product_code);
CREATE INDEX idx_call_record_scene ON call_record(scene_code);
CREATE INDEX idx_call_record_cache_hit ON call_record(cache_hit);

-- 8. 日账单表
CREATE TABLE IF NOT EXISTS billing_daily (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    caller_id BIGINT NOT NULL,
    vendor_id BIGINT,
    data_type VARCHAR(50) NOT NULL,
    call_count BIGINT NOT NULL DEFAULT 0,
    success_count BIGINT NOT NULL DEFAULT 0,
    fail_count BIGINT NOT NULL DEFAULT 0,
    total_cost DECIMAL(12, 4) NOT NULL DEFAULT 0,
    avg_latency INTEGER,
    billing_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_tenant FOREIGN KEY (tenant_id) REFERENCES tenant_info(id),
    CONSTRAINT fk_billing_caller FOREIGN KEY (caller_id) REFERENCES caller_info(id),
    UNIQUE(tenant_id, caller_id, vendor_id, data_type, billing_date)
);

CREATE INDEX idx_billing_date ON billing_daily(billing_date);
CREATE INDEX idx_billing_tenant ON billing_daily(tenant_id);

CREATE TABLE IF NOT EXISTS billing_daily_event (
    request_id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. 用户信息表
CREATE TABLE IF NOT EXISTS user_info (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    nickname VARCHAR(100),
    password VARCHAR(128) NOT NULL,
    real_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    tenant_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    last_login_time TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant_info(id)
);

CREATE INDEX idx_user_username ON user_info(username);
CREATE INDEX idx_user_tenant ON user_info(tenant_id);

-- 10. 角色信息表
CREATE TABLE IF NOT EXISTS role_info (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

-- 11. 用户角色关联表
CREATE TABLE IF NOT EXISTS user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES user_info(id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES role_info(id),
    UNIQUE(user_id, role_id)
);

-- 12. 告警规则表
CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    target_type VARCHAR(50),
    target_id BIGINT,
    metric_name VARCHAR(50) NOT NULL,
    condition VARCHAR(20) NOT NULL,
    threshold DECIMAL(10, 2) NOT NULL,
    time_window INTEGER NOT NULL DEFAULT 300,
    severity VARCHAR(20) NOT NULL DEFAULT 'warning',
    notification_channels VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_alert_rule_type ON alert_rule(rule_type);
CREATE INDEX idx_alert_rule_status ON alert_rule(status);

-- 13. 告警记录表
CREATE TABLE IF NOT EXISTS alert_record (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    tenant_id BIGINT,
    alert_type VARCHAR(20) NOT NULL,
    alert_title VARCHAR(200) NOT NULL,
    alert_content TEXT,
    metric_value DECIMAL(10, 2),
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'firing',
    fired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by BIGINT,
    resolution TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_record_rule FOREIGN KEY (rule_id) REFERENCES alert_rule(id)
);

CREATE INDEX idx_alert_record_status ON alert_record(status);
CREATE INDEX idx_alert_record_fired ON alert_record(fired_at);

-- 14. 熔断记录表
CREATE TABLE IF NOT EXISTS circuit_breaker (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'closed',
    failure_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    last_failure_time TIMESTAMP,
    opened_at TIMESTAMP,
    half_open_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cb_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_info(id),
    UNIQUE(vendor_id, data_type)
);

CREATE INDEX idx_cb_vendor ON circuit_breaker(vendor_id);

-- 15. 操作日志表
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    tenant_id BIGINT,
    operation_type VARCHAR(20) NOT NULL,
    operation_module VARCHAR(50) NOT NULL,
    module VARCHAR(100),
    operation VARCHAR(200),
    operation_content TEXT,
    request_url VARCHAR(200),
    request_method VARCHAR(10),
    method VARCHAR(200),
    request_params JSONB,
    params TEXT,
    response_code VARCHAR(10),
    result TEXT,
    ip_address VARCHAR(50),
    ip VARCHAR(50),
    location VARCHAR(200),
    user_agent VARCHAR(500),
    execution_time INTEGER,
    duration INTEGER,
    status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_operation_log_user ON operation_log(user_id);
CREATE INDEX idx_operation_log_time ON operation_log(created_at);
CREATE INDEX idx_operation_log_module ON operation_log(operation_module);

-- 提交
COMMIT;

-- 计费规则表
CREATE TABLE IF NOT EXISTS billing_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL DEFAULT '默认计费规则',
    vendor_id BIGINT,
    vendor_name VARCHAR(100),
    interface_id BIGINT,
    interface_code VARCHAR(64),
    interface_name VARCHAR(100),
    -- 仅作为接口归属的数据统计快照，不参与计费规则匹配。
    data_type VARCHAR(50),
    unit_price DECIMAL(10, 4) NOT NULL DEFAULT 0,
    tier_min INTEGER DEFAULT 0,
    tier_max INTEGER,
    discount DECIMAL(3, 2) DEFAULT 1.00,
    status VARCHAR(20) DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_rule_vendor_interface
    ON billing_rule(vendor_id, interface_id);

-- 一条厂商+接口规则可包含多个连续的阶梯区间。
CREATE TABLE IF NOT EXISTS billing_rule_tier (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    tier_min BIGINT NOT NULL,
    tier_max BIGINT,
    discount DECIMAL(5, 4) NOT NULL DEFAULT 1.0000,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_rule_tier_rule
        FOREIGN KEY (rule_id) REFERENCES billing_rule(id) ON DELETE CASCADE,
    CONSTRAINT chk_billing_rule_tier_range
        CHECK (tier_min >= 0 AND (tier_max IS NULL OR tier_max > tier_min)),
    CONSTRAINT chk_billing_rule_tier_discount
        CHECK (discount > 0 AND discount <= 1),
    UNIQUE(rule_id, tier_min)
);

CREATE INDEX IF NOT EXISTS idx_billing_rule_tier_rule
    ON billing_rule_tier(rule_id, sort_order);

-- 阶梯规则按自然月累计厂商+接口调用量。
CREATE TABLE IF NOT EXISTS billing_tier_usage (
    rule_id BIGINT NOT NULL,
    billing_period DATE NOT NULL,
    call_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id, billing_period),
    CONSTRAINT chk_billing_tier_usage_count CHECK (call_count >= 0)
);

CREATE TABLE IF NOT EXISTS billing_tier_usage_event (
    request_id VARCHAR(64) PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    billing_period DATE NOT NULL,
    usage_before BIGINT NOT NULL,
    call_count BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_billing_tier_usage_event_count
        CHECK (usage_before >= 0 AND call_count > 0)
);

CREATE INDEX IF NOT EXISTS idx_billing_tier_usage_event_rule_period
    ON billing_tier_usage_event(rule_id, billing_period);

-- 厂商配置扩展表（配置中心用）
CREATE TABLE IF NOT EXISTS vendor_config_extended (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    config_type VARCHAR(20) DEFAULT 'string',
    description VARCHAR(200),
    is_encrypted BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    status VARCHAR(20) DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vendor_config_extended_vendor ON vendor_config_extended(vendor_id);

-- 灰度规则表
CREATE TABLE IF NOT EXISTS gray_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    version VARCHAR(50),
    weight INTEGER DEFAULT 10,
    condition_type VARCHAR(50) DEFAULT 'random',
    condition_value VARCHAR(200),
    description VARCHAR(500),
    status VARCHAR(20) DEFAULT 'active',
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gray_rule_service ON gray_rule(service_name);
CREATE INDEX idx_gray_rule_status ON gray_rule(status);

-- 数据血缘表（数据溯源用）
CREATE TABLE IF NOT EXISTS data_lineage (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    target_name VARCHAR(100) NOT NULL,
    relation_type VARCHAR(50),
    transform_rule TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_data_lineage_source ON data_lineage(source_type, source_id);
CREATE INDEX idx_data_lineage_target ON data_lineage(target_type, target_id);

-- 数据质量规则表
CREATE TABLE IF NOT EXISTS quality_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(50) NOT NULL UNIQUE,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    target_table VARCHAR(100) NOT NULL,
    target_column VARCHAR(100),
    rule_config JSONB,
    threshold DECIMAL(10, 2),
    status VARCHAR(20) DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false
);

CREATE INDEX idx_quality_rule_table ON quality_rule(target_table);
CREATE INDEX idx_quality_rule_type ON quality_rule(rule_type);

-- 数据质量评分表
CREATE TABLE IF NOT EXISTS quality_score (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    score_date DATE NOT NULL,
    score_value DECIMAL(5, 2) NOT NULL,
    record_count BIGINT,
    pass_count BIGINT,
    fail_count BIGINT,
    details JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quality_score_rule FOREIGN KEY (rule_id) REFERENCES quality_rule(id),
    UNIQUE(rule_id, score_date)
);

CREATE INDEX idx_quality_score_date ON quality_score(score_date);
CREATE INDEX idx_quality_score_rule ON quality_score(rule_id);

-- =====================================================
-- 更新记录
-- 2026-07-21: 引入模板化、版本化计费方案与事件账本
-- 2026-04-24: 添加缺失字段 (nickname, updated_by, resolved_by等)
-- 2026-04-24: 添加 data_lineage, quality_rule, quality_score 表
-- =====================================================

\ir migrations/V021__create_billing_plan_and_event_ledger.sql
