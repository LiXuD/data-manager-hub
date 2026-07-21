-- 厂商+接口仍唯一确定一条计费规则；阶梯作为该规则的明细配置。
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

-- 兼容旧的单档阶梯数据：tier_min 之前按原价，之后沿用旧折扣且不封顶。
INSERT INTO billing_rule_tier (rule_id, tier_min, tier_max, discount, sort_order)
SELECT br.id, 0, br.tier_min, 1.0000, 0
FROM billing_rule br
WHERE br.billing_type = 'TIERED'
  AND COALESCE(br.tier_min, 0) > 0
  AND NOT EXISTS (
      SELECT 1 FROM billing_rule_tier tier WHERE tier.rule_id = br.id
  );

INSERT INTO billing_rule_tier (rule_id, tier_min, tier_max, discount, sort_order)
SELECT br.id,
       COALESCE(br.tier_min, 0),
       NULL,
       COALESCE(br.discount, 1.0000),
       CASE WHEN COALESCE(br.tier_min, 0) > 0 THEN 1 ELSE 0 END
FROM billing_rule br
WHERE br.billing_type = 'TIERED'
  AND NOT EXISTS (
      SELECT 1
      FROM billing_rule_tier tier
      WHERE tier.rule_id = br.id
        AND tier.tier_min = COALESCE(br.tier_min, 0)
  );

COMMENT ON TABLE billing_rule_tier IS '厂商+接口计费规则的阶梯明细';
COMMENT ON COLUMN billing_rule_tier.tier_min IS '调用量下限（含）';
COMMENT ON COLUMN billing_rule_tier.tier_max IS '调用量上限（不含），NULL表示无上限';
COMMENT ON COLUMN billing_rule_tier.discount IS '该调用量区间的折扣率，1表示原价';
COMMENT ON COLUMN billing_rule.tier_min IS '兼容字段；新阶梯配置请使用billing_rule_tier';
COMMENT ON COLUMN billing_rule.tier_max IS '兼容字段；新阶梯配置请使用billing_rule_tier';
COMMENT ON COLUMN billing_rule.discount IS '兼容字段；新阶梯配置请使用billing_rule_tier';
