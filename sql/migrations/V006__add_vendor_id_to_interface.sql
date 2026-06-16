-- =====================================================
-- 迁移脚本: V006__add_vendor_id_to_interface
-- 描述: 为 api_interface 表添加 vendor_id 字段
-- 创建时间: 2026-05-03
-- =====================================================

-- 1. 添加 vendor_id 字段到 api_interface 表
ALTER TABLE api_interface ADD COLUMN IF NOT EXISTS vendor_id BIGINT DEFAULT NULL;

-- 2. 添加外键约束
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_api_interface_vendor'
        AND table_name = 'api_interface'
    ) THEN
        ALTER TABLE api_interface
        ADD CONSTRAINT fk_api_interface_vendor
        FOREIGN KEY (vendor_id) REFERENCES vendor_info(id);
    END IF;
END $$;

-- 3. 添加索引
CREATE INDEX IF NOT EXISTS idx_api_interface_vendor ON api_interface(vendor_id);

-- 4. 添加注释
COMMENT ON COLUMN api_interface.vendor_id IS '所属厂商ID';

-- 5. 移除原有的 NOT NULL 约束（如果数据类型可选）
-- ALTER TABLE api_interface ALTER COLUMN data_type_id DROP NOT NULL;
