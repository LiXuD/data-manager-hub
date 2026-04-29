-- 添加计费类型字段
ALTER TABLE billing_rule
ADD COLUMN IF NOT EXISTS billing_type VARCHAR(20) DEFAULT 'STANDARD';

-- 添加检查约束，确保计费类型有效
ALTER TABLE billing_rule
ADD CONSTRAINT chk_billing_type
CHECK (billing_type IN ('STANDARD', 'TIERED', 'DYNAMIC'));

-- 为现有数据设置默认值
UPDATE billing_rule
SET billing_type = CASE
    WHEN sla_threshold IS NOT NULL AND compensation_rate IS NOT NULL THEN 'DYNAMIC'
    WHEN tier_min IS NOT NULL OR tier_max IS NOT NULL OR discount IS NOT NULL THEN 'TIERED'
    ELSE 'STANDARD'
END
WHERE billing_type IS NULL;

-- 创建索引以便按计费类型查询
CREATE INDEX IF NOT EXISTS idx_billing_rule_type ON billing_rule(billing_type);

-- 添加注释
COMMENT ON COLUMN billing_rule.billing_type IS '计费类型: STANDARD-标准计费, TIERED-阶梯计费, DYNAMIC-动态计费(SLA补偿)';
