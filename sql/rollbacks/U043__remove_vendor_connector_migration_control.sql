-- Refuse rollback while a vendor migration is active or has completed its observation gate.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM vendor_connector_migration)
       OR EXISTS (SELECT 1 FROM billing_event WHERE pipeline_version IS NOT NULL OR snapshot_hash IS NOT NULL) THEN
        RAISE EXCEPTION
            'Refusing V043 rollback: migration or connector billing facts must remain historically explainable';
    END IF;
END $$;

DELETE FROM role_permission
WHERE permission_id IN (
    SELECT id FROM permission WHERE permission_code = 'connector-plugin:migrate'
);
DELETE FROM permission WHERE permission_code = 'connector-plugin:migrate';

DROP TABLE IF EXISTS vendor_connector_migration;

DROP INDEX IF EXISTS idx_call_record_connector_migration_trace;
DROP INDEX IF EXISTS idx_billing_event_connector_trace;
ALTER TABLE billing_event
    DROP COLUMN IF EXISTS snapshot_hash,
    DROP COLUMN IF EXISTS pipeline_version,
    DROP COLUMN IF EXISTS plugin_version,
    DROP COLUMN IF EXISTS plugin_id;
