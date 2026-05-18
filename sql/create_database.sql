-- =====================================================
-- 数据管理平台 - 数据库创建脚本
-- PostgreSQL 本地环境 (localhost:5342)
-- 用户: postgres
-- =====================================================

-- 1. 创建数据库
DROP DATABASE IF EXISTS dataplatform;
CREATE DATABASE dataplatform
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1
    TEMPLATE template0;

-- 2. 连接数据库
\c dataplatform postgres;

-- 授权
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;

-- =====================================================
-- DDL 脚本
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_tenant_code ON tenant_info(tenant_code);
CREATE INDEX IF NOT EXISTS idx_tenant_status ON tenant_info(status);

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
    contract_start DATE,
    contract_end DATE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_vendor_code ON vendor_info(vendor_code);
CREATE INDEX IF NOT EXISTS idx_vendor_status ON vendor_info(status);

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
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_datatype_code ON data_type(data_type_code);

-- 4. 厂商配置表
CREATE TABLE IF NOT EXISTS vendor_config (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL,
    data_type_id BIGINT NOT NULL,
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
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_vendor_config_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_info(id),
    CONSTRAINT fk_vendor_config_datatype FOREIGN KEY (data_type_id) REFERENCES data_type(id),
    UNIQUE(vendor_id, data_type_id)
);

CREATE INDEX IF NOT EXISTS idx_vendor_config_vendor ON vendor_config(vendor_id);
CREATE INDEX IF NOT EXISTS idx_vendor_config_datatype ON vendor_config(data_type_id);

-- 5. 调用方信息表
CREATE TABLE IF NOT EXISTS caller_info (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    caller_code VARCHAR(50) NOT NULL UNIQUE,
    caller_name VARCHAR(100) NOT NULL,
    caller_type VARCHAR(20) NOT NULL DEFAULT 'internal',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    description VARCHAR(500),
    max_daily_calls BIGINT DEFAULT 10000,
    rate_limit_per_minute INTEGER DEFAULT 100,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_caller_tenant FOREIGN KEY (tenant_id) REFERENCES tenant_info(id)
);

CREATE INDEX IF NOT EXISTS idx_caller_code ON caller_info(caller_code);
CREATE INDEX IF NOT EXISTS idx_caller_tenant ON caller_info(tenant_id);
CREATE INDEX IF NOT EXISTS idx_caller_status ON caller_info(status);

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

CREATE INDEX IF NOT EXISTS idx_caller_product_caller ON caller_product(caller_id);
CREATE INDEX IF NOT EXISTS idx_caller_product_code ON caller_product(product_code);
CREATE INDEX IF NOT EXISTS idx_caller_product_status ON caller_product(status);

-- 6. API Key 表
CREATE TABLE IF NOT EXISTS api_key (
    id BIGSERIAL PRIMARY KEY,
    caller_id BIGINT NOT NULL,
    key_value VARCHAR(64) NOT NULL UNIQUE,
    key_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_apikey_caller FOREIGN KEY (caller_id) REFERENCES caller_info(id)
);

CREATE INDEX IF NOT EXISTS idx_apikey_value ON api_key(key_value);
CREATE INDEX IF NOT EXISTS idx_apikey_caller ON api_key(caller_id);
CREATE INDEX IF NOT EXISTS idx_apikey_status ON api_key(status);

CREATE TABLE IF NOT EXISTS api_key_product (
    id BIGSERIAL PRIMARY KEY,
    api_key_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT fk_apikey_product_key FOREIGN KEY (api_key_id) REFERENCES api_key(id),
    CONSTRAINT fk_apikey_product_product FOREIGN KEY (product_id) REFERENCES caller_product(id),
    UNIQUE(api_key_id, product_id)
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
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_call_scene_code ON call_scene(scene_code);
CREATE INDEX IF NOT EXISTS idx_call_scene_status ON call_scene(status);

-- 7. 调用记录表 (按月分区)
CREATE TABLE IF NOT EXISTS call_record (
    id BIGSERIAL,
    tenant_id BIGINT NOT NULL,
    caller_id BIGINT NOT NULL,
    api_key_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    vendor_code VARCHAR(50) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
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
    cost DECIMAL(10, 4) DEFAULT 0,
    cached BOOLEAN NOT NULL DEFAULT false,
    use_cache BOOLEAN NOT NULL DEFAULT false,
    cache_days INTEGER,
    cache_hit BOOLEAN NOT NULL DEFAULT false,
    cache_scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
    cache_source_record_id BIGINT,
    request_time TIMESTAMP,
    response_at TIMESTAMP,
    call_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, call_time),
    CONSTRAINT fk_record_tenant FOREIGN KEY (tenant_id) REFERENCES tenant_info(id),
    CONSTRAINT fk_record_caller FOREIGN KEY (caller_id) REFERENCES caller_info(id),
    CONSTRAINT fk_record_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_info(id)
) PARTITION BY RANGE (call_time);

ALTER TABLE call_record ADD COLUMN IF NOT EXISTS api_code VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS api_key_id BIGINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS vendor_code VARCHAR(50);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS product_id BIGINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS product_code VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS product_name VARCHAR(100);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS scene_code VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS scene_name VARCHAR(100);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS data_type VARCHAR(50);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS data_type_code VARCHAR(50);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS success BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS error_code VARCHAR(20);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS error_msg VARCHAR(500);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS latency INTEGER;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS cached BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS response_at TIMESTAMP;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS duration_ms INTEGER;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS request_params JSONB;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS use_cache BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS cache_days INTEGER;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS cache_hit BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS cache_scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL';
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS cache_source_record_id BIGINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS request_time TIMESTAMP;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS call_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 创建分区函数
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

-- 初始化当前月和下月分区
SELECT create_monthly_partition(CURRENT_DATE::DATE);
SELECT create_monthly_partition((CURRENT_DATE + INTERVAL '1 month')::DATE);

CREATE INDEX IF NOT EXISTS idx_call_record_caller ON call_record(caller_id);
CREATE INDEX IF NOT EXISTS idx_call_record_vendor ON call_record(vendor_id);
CREATE INDEX IF NOT EXISTS idx_call_record_time ON call_record(call_time);
CREATE INDEX IF NOT EXISTS idx_call_record_success ON call_record(success);
CREATE INDEX IF NOT EXISTS idx_call_record_cache_lookup ON call_record(api_code, request_hash, call_time);
CREATE INDEX IF NOT EXISTS idx_call_record_product ON call_record(product_code);
CREATE INDEX IF NOT EXISTS idx_call_record_scene ON call_record(scene_code);
CREATE INDEX IF NOT EXISTS idx_call_record_cache_hit ON call_record(cache_hit);

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

CREATE INDEX IF NOT EXISTS idx_billing_daily_caller ON billing_daily(caller_id);
CREATE INDEX IF NOT EXISTS idx_billing_daily_date ON billing_daily(billing_date);
CREATE INDEX IF NOT EXISTS idx_billing_daily_tenant ON billing_daily(tenant_id);

CREATE TABLE IF NOT EXISTS billing_daily_event (
    request_id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. 用户表
CREATE TABLE IF NOT EXISTS user_info (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_user_username ON user_info(username);
CREATE INDEX IF NOT EXISTS idx_user_tenant ON user_info(tenant_id);

-- 10. 角色表
CREATE TABLE IF NOT EXISTS role_info (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_role_code ON role_info(role_code);

-- 11. 用户角色关联表
CREATE TABLE IF NOT EXISTS user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_userrole_user FOREIGN KEY (user_id) REFERENCES user_info(id),
    CONSTRAINT fk_userrole_role FOREIGN KEY (role_id) REFERENCES role_info(id),
    UNIQUE(user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_userrole_user ON user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_userrole_role ON user_role(role_id);

-- 12. 告警规则表
CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT,
    condition_type VARCHAR(20) NOT NULL,
    threshold_value DECIMAL(10, 4),
    time_window_minutes INTEGER DEFAULT 5,
    notify_channels JSONB DEFAULT '["email"]',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_alertrule_type ON alert_rule(rule_type);
CREATE INDEX IF NOT EXISTS idx_alertrule_status ON alert_rule(status);

-- 13. 告警记录表
CREATE TABLE IF NOT EXISTS alert_record (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    alert_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    alert_level VARCHAR(20) NOT NULL DEFAULT 'warning',
    alert_message VARCHAR(500),
    triggered_value DECIMAL(10, 4),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    resolved_at TIMESTAMP,
    resolved_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alertrecord_rule FOREIGN KEY (rule_id) REFERENCES alert_rule(id)
);

CREATE INDEX IF NOT EXISTS idx_alertrecord_rule ON alert_record(rule_id);
CREATE INDEX IF NOT EXISTS idx_alertrecord_time ON alert_record(alert_time);
CREATE INDEX IF NOT EXISTS idx_alertrecord_status ON alert_record(status);

-- 14. 熔断记录表
CREATE TABLE IF NOT EXISTS circuit_breaker (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL,
    data_type_id BIGINT,
    state VARCHAR(20) NOT NULL DEFAULT 'closed',
    failure_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    last_failure_time TIMESTAMP,
    last_success_time TIMESTAMP,
    opened_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    CONSTRAINT fk_cb_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_info(id),
    CONSTRAINT fk_cb_datatype FOREIGN KEY (data_type_id) REFERENCES data_type(id)
);

CREATE INDEX IF NOT EXISTS idx_circuit_vendor ON circuit_breaker(vendor_id);
CREATE INDEX IF NOT EXISTS idx_circuit_state ON circuit_breaker(state);

-- 15. 操作日志表
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    operation_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id BIGINT,
    operation_detail JSONB,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_oplog_user ON operation_log(user_id);
CREATE INDEX IF NOT EXISTS idx_oplog_type ON operation_log(operation_type);
CREATE INDEX IF NOT EXISTS idx_oplog_time ON operation_log(created_at);

-- =====================================================
-- 初始数据
-- =====================================================

-- 租户
INSERT INTO tenant_info (tenant_code, tenant_name, tenant_type, status, contact_person, contact_phone, contact_email)
VALUES 
    ('T001', '测试租户', 'enterprise', 'active', '张三', '13800138000', 'zhangsan@example.com'),
    ('T002', '银行A', 'enterprise', 'active', '李四', '13900139000', 'libank@example.com')
ON CONFLICT (tenant_code) DO NOTHING;

-- 厂商
INSERT INTO vendor_info (vendor_code, vendor_name, vendor_type, status, description)
VALUES 
    ('V001', '工商数据厂商A', 'business_info', 'active', '提供企业工商信息查询'),
    ('V002', '工商数据厂商B', 'business_info', 'active', '提供企业工商信息查询(备选)'),
    ('V003', '手机验证厂商', 'phone_verify', 'active', '提供手机号实名验证')
ON CONFLICT (vendor_code) DO NOTHING;

-- 数据类型
INSERT INTO data_type (data_type_code, data_type_name, data_category, description, pricing_model, unit_price)
VALUES 
    ('BUSINESS_INFO', '企业工商信息', 'business_info', '企业基本信息、股东、变更等', 'per_call', 0.50),
    ('PERSON_INFO', '个人信息', 'personal_info', '姓名、身份证、手机号等', 'per_call', 0.30),
    ('PHONE_VERIFY', '手机号验证', 'phone_verify', '手机号实名验证', 'per_call', 0.10)
ON CONFLICT (data_type_code) DO NOTHING;

-- 调用方
INSERT INTO caller_info (tenant_id, caller_code, caller_name, caller_type, status, description)
VALUES 
    (2, 'C001', '风控系统', 'internal', 'active', '银行风控系统'),
    (2, 'C002', '信贷系统', 'internal', 'active', '银行信贷系统')
ON CONFLICT (caller_code) DO NOTHING;

-- 角色
INSERT INTO role_info (role_code, role_name, description)
VALUES 
    ('ROLE_ADMIN', '系统管理员', '系统管理权限'),
    ('ROLE_USER', '普通用户', '基本使用权限')
ON CONFLICT (role_code) DO NOTHING;

-- =====================================================
-- 执行完成
-- =====================================================

SELECT '数据库创建完成!' AS message;
