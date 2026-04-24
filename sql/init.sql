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
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_vendor_config_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_info(id),
    CONSTRAINT fk_vendor_config_datatype FOREIGN KEY (data_type_id) REFERENCES data_type(id),
    UNIQUE(vendor_id, data_type_id)
);

CREATE INDEX idx_vendor_config_vendor ON vendor_config(vendor_id);
CREATE INDEX idx_vendor_config_datatype ON vendor_config(data_type_id);

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

-- 6. API Key表
CREATE TABLE IF NOT EXISTS api_key (
    id BIGSERIAL PRIMARY KEY,
    caller_id BIGINT NOT NULL,
    api_key VARCHAR(64) NOT NULL UNIQUE,
    api_secret VARCHAR(128) NOT NULL,
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

-- 7. 调用记录表 (分区表)
CREATE TABLE IF NOT EXISTS call_record (
    id BIGSERIAL,
    request_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    caller_id BIGINT NOT NULL,
    api_key_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    vendor_code VARCHAR(50) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    request_params JSONB,
    response_data JSONB,
    success BOOLEAN NOT NULL DEFAULT true,
    error_code VARCHAR(20),
    error_msg VARCHAR(500),
    latency INTEGER,
    cost DECIMAL(10, 4),
    cached BOOLEAN NOT NULL DEFAULT false,
    call_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, call_time)
) PARTITION BY RANGE (call_time);

-- 分区策略: 按月分区，提前创建未来12个月的分区
CREATE TABLE IF NOT EXISTS call_record_2026_04 PARTITION OF call_record
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE IF NOT EXISTS call_record_2026_05 PARTITION OF call_record
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE IF NOT EXISTS call_record_2026_06 PARTITION OF call_record
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- 索引
CREATE INDEX idx_call_record_request ON call_record(request_id);
CREATE INDEX idx_call_record_tenant ON call_record(tenant_id);
CREATE INDEX idx_call_record_caller ON call_record(caller_id);
CREATE INDEX idx_call_record_vendor ON call_record(vendor_id);
CREATE INDEX idx_call_record_time ON call_record(call_time);

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

-- =====================================================
-- 初始数据
-- =====================================================

-- 租户数据
INSERT INTO tenant_info (tenant_code, tenant_name, tenant_type, status, contact_person, contact_email)
VALUES 
    ('TENANT001', '测试租户', 'enterprise', 'active', '张三', 'zhangsan@example.com'),
    ('TENANT002', '银行A', 'enterprise', 'active', '李四', 'lisi@banka.com');

-- 厂商数据
INSERT INTO vendor_info (vendor_code, vendor_name, vendor_type, status, contact_person)
VALUES 
    ('tianyancha', '天眼查', 'enterprise_data', 'active', '天眼查客服'),
    ('qichacha', '企查查', 'enterprise_data', 'active', '企查查客服'),
    ('yidun', '网易易盾', 'security', 'active', '易盾客服');

-- 数据类型
INSERT INTO data_type (data_type_code, data_type_name, data_category, pricing_model, unit_price)
VALUES 
    ('company_info', '工商信息', 'enterprise', 'per_call', 0.30),
    ('person_phone', '手机号验证', 'personal', 'per_call', 0.15),
    ('id_card_verify', '身份证验证', 'personal', 'per_call', 0.20);

-- 角色
INSERT INTO role_info (role_code, role_name, description)
VALUES 
    ('ADMIN', '系统管理员', '拥有所有权限'),
    ('TENANT_ADMIN', '租户管理员', '管理租户内的所有资源'),
    ('OPERATOR', '操作员', '可以进行日常操作'),
    ('VIEWER', '只读用户', '只能查看数据');

-- 告警规则示例
INSERT INTO alert_rule (rule_name, rule_type, metric_name, condition, threshold, time_window, severity, notification_channels, status)
VALUES 
    ('厂商响应超时告警', 'vendor_latency', 'avg_latency', '>', 5000, 300, 'warning', 'email,sms', 'active'),
    ('API调用失败率告警', 'vendor_error', 'error_rate', '>', 10, 300, 'critical', 'email,sms,phone', 'active'),
    ('额度不足告警', 'quota', 'quota_used_percent', '>', 80, 60, 'warning', 'email', 'active'),
    ('熔断触发告警', 'circuit', 'circuit_state', '=', 1, 0, 'critical', 'email,sms', 'active');

-- 提交
COMMIT;

-- 计费规则表
CREATE TABLE IF NOT EXISTS billing_rule (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT,
    vendor_name VARCHAR(100),
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
    source_system VARCHAR(100) NOT NULL,
    source_table VARCHAR(100) NOT NULL,
    source_column VARCHAR(100),
    target_system VARCHAR(100) NOT NULL,
    target_table VARCHAR(100) NOT NULL,
    target_column VARCHAR(100),
    transformation_type VARCHAR(50),
    transformation_logic TEXT,
    lineage_level INTEGER DEFAULT 1,
    status VARCHAR(20) DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_data_lineage_source ON data_lineage(source_system, source_table);
CREATE INDEX idx_data_lineage_target ON data_lineage(target_system, target_table);

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
-- 2026-04-24: 添加缺失字段 (nickname, updated_by, resolved_by等)
-- 2026-04-24: 添加 data_lineage, quality_rule, quality_score 表
-- =====================================================
