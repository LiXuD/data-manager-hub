-- Refuse destructive rollback once connector facts have been created.
DO $$
BEGIN
    IF EXISTS (
           SELECT 1 FROM connector_plugin
           WHERE plugin_id <> 'legacy-http'
              OR provider <> 'internal'
              OR status <> 'ACTIVE'
              OR deleted = TRUE
           LIMIT 1
       )
       OR EXISTS (
           SELECT 1 FROM connector_plugin_version
           WHERE plugin_id <> 'legacy-http'
              OR version <> '1.0.0'
              OR artifact_uri <> 'builtin://legacy-http/1.0.0'
              OR artifact_sha256 <> 'cc14cbc264ed84857ff56dc61ab0e4ceca128cf3c4bb35042814ef907211af90'
              OR entry_class <> 'com.dataplatform.common.plugin.legacy.LegacyHttpConnectorPlugin'
              OR status <> 'ACTIVE'
           LIMIT 1
       )
       OR EXISTS (SELECT 1 FROM vendor_connector_version LIMIT 1)
       OR EXISTS (SELECT 1 FROM vendor_connector_test_fact LIMIT 1)
       OR EXISTS (SELECT 1 FROM connector_plugin_activation LIMIT 1)
       OR EXISTS (
           SELECT 1 FROM vendor_config
           WHERE runtime_mode <> 'LEGACY'
              OR active_connector_version_id IS NOT NULL
              OR connector_version <> 0
           LIMIT 1
       )
       OR EXISTS (
           SELECT 1 FROM call_record
           WHERE plugin_id IS NOT NULL
              OR plugin_version IS NOT NULL
              OR pipeline_version IS NOT NULL
              OR snapshot_hash IS NOT NULL
           LIMIT 1
       ) THEN
        RAISE EXCEPTION 'U042 refused: connector plugin or runtime trace data exists; restore a backup or perform a reviewed forward recovery';
    END IF;
END $$;

DELETE FROM role_permission
WHERE permission_id IN (
    SELECT id FROM permission WHERE permission_code LIKE 'connector-plugin:%'
);
DELETE FROM permission WHERE permission_code LIKE 'connector-plugin:%';

DELETE FROM connector_plugin_version
WHERE plugin_id = 'legacy-http' AND version = '1.0.0';
DELETE FROM connector_plugin WHERE plugin_id = 'legacy-http';

DROP INDEX IF EXISTS idx_call_record_pipeline_trace;
DROP INDEX IF EXISTS idx_call_record_plugin_trace;
ALTER TABLE call_record
    DROP COLUMN IF EXISTS snapshot_hash,
    DROP COLUMN IF EXISTS pipeline_version,
    DROP COLUMN IF EXISTS plugin_version,
    DROP COLUMN IF EXISTS plugin_id;

DROP TABLE IF EXISTS connector_plugin_activation;

ALTER TABLE vendor_config DROP CONSTRAINT IF EXISTS fk_vendor_config_active_connector_version;
DROP INDEX IF EXISTS idx_vendor_config_active_connector;
ALTER TABLE vendor_config
    DROP CONSTRAINT IF EXISTS ck_vendor_config_runtime_mode,
    DROP COLUMN IF EXISTS connector_version,
    DROP COLUMN IF EXISTS active_connector_version_id,
    DROP COLUMN IF EXISTS runtime_mode;

DROP TRIGGER IF EXISTS trg_vendor_connector_test_fact_immutable ON vendor_connector_test_fact;
DROP TABLE IF EXISTS vendor_connector_test_fact;
DROP FUNCTION IF EXISTS reject_vendor_connector_test_fact_mutation();
DROP TABLE IF EXISTS vendor_connector_version;
DROP TABLE IF EXISTS connector_plugin_version;
DROP TABLE IF EXISTS connector_plugin;
