-- 退役旧版 billing_rule 计费规则体系。
-- 计费已切换到版本化 billing_plan + 不可变事件账本，旧规则链路已无运行时调用。
-- 依赖顺序：先删引用 rule_id 的用量/阶梯表，再删主表。
DROP TABLE IF EXISTS billing_tier_usage_event CASCADE;
DROP TABLE IF EXISTS billing_tier_usage CASCADE;
DROP TABLE IF EXISTS billing_rule_tier CASCADE;
DROP TABLE IF EXISTS billing_rule CASCADE;
