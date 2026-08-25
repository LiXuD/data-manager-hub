-- Prepare fields consumed by legacy forward migrations without changing an
-- already-published Liquibase changeset or assuming interface_param exists.
ALTER TABLE vendor_config
    ADD COLUMN IF NOT EXISTS sign_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS encrypt_type VARCHAR(32);

ALTER TABLE IF EXISTS interface_param
    ADD COLUMN IF NOT EXISTS validation_rule VARCHAR(256);
