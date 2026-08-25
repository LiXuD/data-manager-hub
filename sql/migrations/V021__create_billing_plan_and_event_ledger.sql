-- 模板、版本化计费方案、计量用量账户和不可变事件账本。
CREATE TABLE IF NOT EXISTS billing_template (
    id BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(40) NOT NULL,
    template_version INTEGER NOT NULL DEFAULT 1,
    template_name VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    description VARCHAR(500),
    config_schema TEXT NOT NULL DEFAULT '{}',
    supports_quantity BOOLEAN NOT NULL DEFAULT true,
    supports_cycle BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(template_code, template_version)
);

INSERT INTO billing_template(template_code, template_version, template_name, category, description, supports_quantity, supports_cycle)
VALUES
    ('PER_CALL', 1, '按次计费', 'USAGE', '有效调用数量乘以单价', true, false),
    ('TIERED', 1, '阶梯计费', 'USAGE', '按账期累计用量执行累进阶梯', true, true),
    ('PACKAGE_COUNT', 1, '包次计费', 'PACKAGE', '固定套餐费包含周期用量，超额按单价计费', true, true),
    ('FLAT_PERIOD', 1, '包周期计费', 'PACKAGE', '按日、月或年收取固定费用', false, true),
    ('PER_ITEM', 1, '按返回数据量计费', 'USAGE', '按响应数值或数组长度计费', true, false),
    ('DURATION', 1, '按时长计费', 'USAGE', '按秒、分钟或小时计费', true, false)
ON CONFLICT (template_code, template_version) DO NOTHING;

CREATE TABLE IF NOT EXISTS billing_plan (
    id BIGSERIAL PRIMARY KEY,
    plan_code VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    plan_name VARCHAR(100) NOT NULL,
    vendor_id BIGINT NOT NULL,
    vendor_code VARCHAR(64) NOT NULL,
    vendor_name VARCHAR(100) NOT NULL,
    interface_id BIGINT NOT NULL,
    interface_code VARCHAR(64) NOT NULL,
    interface_name VARCHAR(100) NOT NULL,
    template_code VARCHAR(40) NOT NULL,
    accounting_purpose VARCHAR(30) NOT NULL DEFAULT 'VENDOR_PAYABLE',
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    settlement_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTH',
    pricing_config TEXT NOT NULL DEFAULT '{}',
    metering_config TEXT NOT NULL DEFAULT '{}',
    adjustment_config TEXT NOT NULL DEFAULT '{}',
    contract_fingerprint VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    effective_from TIMESTAMP NOT NULL,
    effective_to TIMESTAMP,
    published_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_billing_plan_effective_range CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_billing_plan_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ACTIVE', 'EXPIRED', 'DISABLED', 'NEEDS_REVIEW')),
    UNIQUE(plan_code, version)
);

CREATE INDEX IF NOT EXISTS idx_billing_plan_resolution
    ON billing_plan(vendor_code, interface_code, effective_from, effective_to, status);
CREATE INDEX IF NOT EXISTS idx_billing_plan_interface
    ON billing_plan(vendor_id, interface_id, accounting_purpose);

CREATE TABLE IF NOT EXISTS billing_plan_tier (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES billing_plan(id) ON DELETE CASCADE,
    tier_min NUMERIC(20, 6) NOT NULL,
    tier_max NUMERIC(20, 6),
    unit_price NUMERIC(20, 8),
    discount NUMERIC(12, 8) NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_billing_plan_tier_range CHECK (tier_min >= 0 AND (tier_max IS NULL OR tier_max > tier_min)),
    CONSTRAINT chk_billing_plan_tier_discount CHECK (discount > 0 AND discount <= 1),
    UNIQUE(plan_id, tier_min)
);

CREATE TABLE IF NOT EXISTS billing_usage_balance (
    plan_id BIGINT NOT NULL REFERENCES billing_plan(id),
    billing_period DATE NOT NULL,
    scope_key VARCHAR(128) NOT NULL,
    used_quantity NUMERIC(20, 6) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(plan_id, billing_period, scope_key),
    CONSTRAINT chk_billing_usage_balance_non_negative CHECK (used_quantity >= 0)
);

CREATE TABLE IF NOT EXISTS billing_event (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    event_type VARCHAR(30) NOT NULL DEFAULT 'USAGE',
    plan_id BIGINT NOT NULL REFERENCES billing_plan(id),
    plan_code VARCHAR(64) NOT NULL,
    plan_version INTEGER NOT NULL,
    template_code VARCHAR(40) NOT NULL,
    accounting_purpose VARCHAR(30) NOT NULL DEFAULT 'VENDOR_PAYABLE',
    original_event_id BIGINT REFERENCES billing_event(id),
    tenant_id BIGINT,
    caller_id BIGINT,
    vendor_id BIGINT NOT NULL,
    vendor_code VARCHAR(64) NOT NULL,
    interface_id BIGINT NOT NULL,
    interface_code VARCHAR(64) NOT NULL,
    data_type VARCHAR(50),
    billable BOOLEAN NOT NULL,
    quantity NUMERIC(20, 6) NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL,
    usage_before NUMERIC(20, 6) NOT NULL DEFAULT 0,
    base_amount NUMERIC(20, 8) NOT NULL DEFAULT 0,
    adjustment_amount NUMERIC(20, 8) NOT NULL DEFAULT 0,
    final_amount NUMERIC(20, 8) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(30) NOT NULL,
    evidence_hash VARCHAR(64),
    decision_detail TEXT,
    pricing_snapshot TEXT NOT NULL,
    billing_period DATE NOT NULL,
    call_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_billing_event_quantity CHECK (event_type = 'REVERSAL' OR quantity >= 0),
    CONSTRAINT chk_billing_event_status CHECK (status IN ('POSTED', 'PENDING_REVIEW', 'REVERSED'))
);

CREATE INDEX IF NOT EXISTS idx_billing_event_period
    ON billing_event(billing_period, vendor_id, interface_id);
CREATE INDEX IF NOT EXISTS idx_billing_event_tenant
    ON billing_event(tenant_id, caller_id, call_time);
CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_event_single_reversal
    ON billing_event(original_event_id) WHERE event_type = 'REVERSAL';
