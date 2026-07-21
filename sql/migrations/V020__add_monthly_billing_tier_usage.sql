-- 阶梯调用量按自然月、厂商+接口唯一规则累计。
CREATE TABLE IF NOT EXISTS billing_tier_usage (
    rule_id BIGINT NOT NULL,
    billing_period DATE NOT NULL,
    call_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id, billing_period),
    CONSTRAINT chk_billing_tier_usage_count CHECK (call_count >= 0)
);

-- 保存每次请求占用前的调用量，使重试请求不会重复推进阶梯。
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

COMMENT ON TABLE billing_tier_usage IS '阶梯计费规则的自然月累计调用量';
COMMENT ON TABLE billing_tier_usage_event IS '阶梯用量占用幂等事件';
COMMENT ON COLUMN billing_tier_usage.billing_period IS '自然月首日';
COMMENT ON COLUMN billing_tier_usage_event.usage_before IS '本次调用计费前的累计调用量';
