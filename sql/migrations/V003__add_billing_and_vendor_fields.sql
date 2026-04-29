-- =====================================================
-- 迁移脚本: V003__add_billing_and_vendor_fields
-- 描述: 添加计费规则SLA字段、厂商备用字段、对账表、厂商密钥
-- 创建时间: 2026-04-26
-- =====================================================

-- 0. vendor_info 表添加 secret_key 字段
ALTER TABLE vendor_info ADD COLUMN IF NOT EXISTS secret_key VARCHAR(256) DEFAULT NULL;
COMMENT ON COLUMN vendor_info.secret_key IS '厂商API密钥';

-- 1. vendor_config 表添加 fallback_vendor_id 字段
ALTER TABLE vendor_config ADD COLUMN IF NOT EXISTS fallback_vendor_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN vendor_config.fallback_vendor_id IS '备用厂商ID';

-- 2. billing_rule 表添加 SLA 相关字段
ALTER TABLE billing_rule ADD COLUMN IF NOT EXISTS sla_threshold INTEGER DEFAULT 2000;
ALTER TABLE billing_rule ADD COLUMN IF NOT EXISTS compensation_rate DECIMAL(5,2) DEFAULT 0.10;

COMMENT ON COLUMN billing_rule.sla_threshold IS 'SLA阈值(毫秒), 超过此时间触发补偿';
COMMENT ON COLUMN billing_rule.compensation_rate IS '补偿系数, 每超100ms减少的费用比例';

-- 添加约束
ALTER TABLE billing_rule ADD CONSTRAINT IF NOT EXISTS chk_compensation_rate CHECK (compensation_rate >= 0 AND compensation_rate <= 1);

-- 3. 创建对账记录表
CREATE TABLE IF NOT EXISTS billing_reconciliation (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_date DATE NOT NULL,
    vendor_id BIGINT NOT NULL,
    vendor_name VARCHAR(128),
    data_type VARCHAR(64),
    platform_call_count BIGINT DEFAULT 0,
    platform_amount DECIMAL(12,2) DEFAULT 0.00,
    vendor_call_count BIGINT DEFAULT 0,
    vendor_amount DECIMAL(12,2) DEFAULT 0.00,
    diff_count BIGINT DEFAULT 0,
    diff_amount DECIMAL(12,2) DEFAULT 0.00,
    diff_rate DECIMAL(5,4) DEFAULT 0.0000,
    status VARCHAR(32) DEFAULT 'pending',
    remark TEXT,
    created_by BIGINT DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_reconciliation_date ON billing_reconciliation(reconciliation_date);
CREATE INDEX IF NOT EXISTS idx_reconciliation_vendor ON billing_reconciliation(vendor_id);
CREATE INDEX IF NOT EXISTS idx_reconciliation_status ON billing_reconciliation(status);
CREATE INDEX IF NOT EXISTS idx_reconciliation_date_vendor ON billing_reconciliation(reconciliation_date, vendor_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_reconciliation_unique ON billing_reconciliation(reconciliation_date, vendor_id, data_type);

-- 添加注释
COMMENT ON TABLE billing_reconciliation IS '计费对账记录表';
COMMENT ON COLUMN billing_reconciliation.id IS '主键ID';
COMMENT ON COLUMN billing_reconciliation.reconciliation_date IS '对账日期';
COMMENT ON COLUMN billing_reconciliation.vendor_id IS '厂商ID';
COMMENT ON COLUMN billing_reconciliation.vendor_name IS '厂商名称';
COMMENT ON COLUMN billing_reconciliation.data_type IS '数据类型';
COMMENT ON COLUMN billing_reconciliation.platform_call_count IS '平台调用次数';
COMMENT ON COLUMN billing_reconciliation.platform_amount IS '平台计费金额';
COMMENT ON COLUMN billing_reconciliation.vendor_call_count IS '厂商调用次数';
COMMENT ON COLUMN billing_reconciliation.vendor_amount IS '厂商账单金额';
COMMENT ON COLUMN billing_reconciliation.diff_count IS '差异次数';
COMMENT ON COLUMN billing_reconciliation.diff_amount IS '差异金额';
COMMENT ON COLUMN billing_reconciliation.diff_rate IS '差异率';
COMMENT ON COLUMN billing_reconciliation.status IS '状态: pending/confirmed/alerted';
COMMENT ON COLUMN billing_reconciliation.remark IS '备注';
