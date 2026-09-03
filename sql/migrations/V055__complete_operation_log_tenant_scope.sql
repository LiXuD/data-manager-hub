-- Preserve tenant ownership for audit records created by authenticated services.
-- Existing historical rows may remain NULL and are visible only to platform admins.
ALTER TABLE operation_log
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_operation_log_tenant_time
    ON operation_log(tenant_id, created_at);
