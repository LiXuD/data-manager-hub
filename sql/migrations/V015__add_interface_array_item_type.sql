-- 为基础类型数组保存明确的 items.type；对象数组仍通过子字段树定义。
ALTER TABLE interface_param
    ADD COLUMN IF NOT EXISTS array_item_type VARCHAR(16);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_interface_param_array_item_type') THEN
        ALTER TABLE interface_param
            ADD CONSTRAINT ck_interface_param_array_item_type
            CHECK (array_item_type IS NULL OR array_item_type IN
                   ('string', 'integer', 'number', 'boolean', 'object'));
    END IF;
END $$;

COMMENT ON COLUMN interface_param.array_item_type IS '数组元素类型；对象数组使用object并由子字段定义';
