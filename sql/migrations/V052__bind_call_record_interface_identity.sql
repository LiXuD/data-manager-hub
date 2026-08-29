-- Persist the canonical interface identity alongside the public interface code.
-- Migration observation is scoped by vendor, interface, pipeline version and snapshot;
-- the existing call_record table previously had no durable interface_id column.
ALTER TABLE call_record
    ADD COLUMN IF NOT EXISTS interface_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_call_record_connector_migration_interface
    ON call_record(vendor_id, interface_id, pipeline_version, snapshot_hash, call_time)
    WHERE interface_id IS NOT NULL
      AND pipeline_version IS NOT NULL
      AND snapshot_hash IS NOT NULL;

COMMENT ON COLUMN call_record.interface_id IS
    'Canonical Masterdata api_interface.id for the interface executed by this call';
