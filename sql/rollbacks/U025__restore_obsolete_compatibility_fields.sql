-- 仅恢复列结构；已迁移的数据继续保留在 constraint_config 与安全流水线中。
ALTER TABLE vendor_config
    ADD COLUMN IF NOT EXISTS sign_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS encrypt_type VARCHAR(32);

ALTER TABLE interface_param
    ADD COLUMN IF NOT EXISTS validation_rule VARCHAR(256);
