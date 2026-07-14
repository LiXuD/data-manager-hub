ALTER TABLE billing_rule
    ADD COLUMN IF NOT EXISTS rule_name VARCHAR(100);

UPDATE billing_rule
SET rule_name = COALESCE(NULLIF(rule_name, ''), '默认计费规则')
WHERE rule_name IS NULL OR rule_name = '';

ALTER TABLE billing_rule
    ALTER COLUMN rule_name SET DEFAULT '默认计费规则',
    ALTER COLUMN rule_name SET NOT NULL;
