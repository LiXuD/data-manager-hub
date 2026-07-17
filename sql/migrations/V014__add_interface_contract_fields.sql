-- 将接口请求参数扩展为请求/响应统一契约字段，并记录响应契约告警。
ALTER TABLE interface_param
    ADD COLUMN IF NOT EXISTS direction VARCHAR(16) NOT NULL DEFAULT 'REQUEST',
    ADD COLUMN IF NOT EXISTS parent_id BIGINT,
    ADD COLUMN IF NOT EXISTS example_value TEXT,
    ADD COLUMN IF NOT EXISTS constraint_config JSONB;

ALTER TABLE interface_param DROP CONSTRAINT IF EXISTS uk_interface_param;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_interface_param_direction') THEN
        ALTER TABLE interface_param
            ADD CONSTRAINT ck_interface_param_direction
            CHECK (direction IN ('REQUEST', 'RESPONSE'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_interface_param_parent ON interface_param(parent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_interface_contract_field
    ON interface_param(interface_id, direction, COALESCE(parent_id, 0), param_name);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_interface_param_parent') THEN
        ALTER TABLE interface_param
            ADD CONSTRAINT fk_interface_param_parent
            FOREIGN KEY (parent_id) REFERENCES interface_param(id) ON DELETE CASCADE;
    END IF;
END $$;

ALTER TABLE call_record
    ADD COLUMN IF NOT EXISTS response_contract_valid BOOLEAN,
    ADD COLUMN IF NOT EXISTS response_contract_errors JSONB;

COMMENT ON COLUMN interface_param.direction IS '字段方向: REQUEST/RESPONSE';
COMMENT ON COLUMN interface_param.parent_id IS '父字段ID，object/array 子结构使用';
COMMENT ON COLUMN interface_param.example_value IS '文档示例值(JSON文本)';
COMMENT ON COLUMN interface_param.constraint_config IS '通用约束(JSON): enum/pattern/minimum/maximum/minLength/maxLength/minItems/maxItems/format';
COMMENT ON COLUMN call_record.response_contract_valid IS '平台响应data是否符合接口契约';
COMMENT ON COLUMN call_record.response_contract_errors IS '响应契约校验错误(JSON数组)';
