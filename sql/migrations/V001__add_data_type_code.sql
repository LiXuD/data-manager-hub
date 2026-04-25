-- =====================================================
-- 迁移脚本: V001__add_data_type_code
-- 描述: 为 vendor_config 表添加 dataTypeCode 冗余字段
-- 创建时间: 2026-04-26
-- =====================================================

-- 1. 添加 dataTypeCode 字段
ALTER TABLE vendor_config ADD COLUMN IF NOT EXISTS data_type_code VARCHAR(50);

-- 2. 从 data_type 表回填数据
UPDATE vendor_config vc
SET data_type_code = dt.data_type_code
FROM data_type dt
WHERE vc.data_type_id = dt.id;

-- 3. 创建索引
CREATE INDEX IF NOT EXISTS idx_vendor_config_data_type_code ON vendor_config(data_type_code);

-- 4. 添加注释
COMMENT ON COLUMN vendor_config.data_type_code IS '数据类型编码(冗余字段)';
