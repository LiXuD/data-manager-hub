-- Tenant ownership is part of the audit visibility contract and is forward-only.
DO $$
BEGIN
    RAISE EXCEPTION 'V055 is forward-only; do not remove operation_log.tenant_id';
END $$;
