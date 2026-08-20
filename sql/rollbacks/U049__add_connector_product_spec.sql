-- V049 can be reversed only while no v2/SIMPLE product fact exists.
-- Liquibase executes this rollback transactionally, so every HALT leaves all V049 objects intact.

DO $$
DECLARE
    blocking_references TEXT;
BEGIN
    SELECT string_agg(reference, ',' ORDER BY reference)
    INTO blocking_references
    FROM (
        SELECT format('plugin:%s:%s@%s', id, plugin_id, version) AS reference
        FROM connector_plugin_version
        WHERE manifest_version = '2' OR authoring_model = 'SIMPLE_CONNECTOR'
        UNION ALL
        SELECT format('connector:%s', id)
        FROM vendor_connector_version
        WHERE authoring_mode = 'SIMPLE_CONNECTOR'
        UNION ALL
        SELECT format('test:%s', id)
        FROM vendor_connector_test_fact
        WHERE authoring_mode = 'SIMPLE_CONNECTOR'
    ) blocking;

    IF blocking_references IS NOT NULL THEN
        RAISE EXCEPTION 'U049 rollback HALT: connector product facts exist: %', blocking_references;
    END IF;
END;
$$;

-- Restore the exact V047 protection surface before removing V049 columns.
CREATE OR REPLACE FUNCTION reject_connector_plugin_version_identity_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'connector_plugin_version is retained permanently; use DISABLED status';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.plugin_id IS DISTINCT FROM OLD.plugin_id
       OR NEW.version IS DISTINCT FROM OLD.version
       OR NEW.spi_version IS DISTINCT FROM OLD.spi_version
       OR NEW.entry_class IS DISTINCT FROM OLD.entry_class
       OR NEW.artifact_uri IS DISTINCT FROM OLD.artifact_uri
       OR NEW.artifact_sha256 IS DISTINCT FROM OLD.artifact_sha256
       OR NEW.detached_signature IS DISTINCT FROM OLD.detached_signature
       OR NEW.signing_key_id IS DISTINCT FROM OLD.signing_key_id
       OR NEW.manifest_json IS DISTINCT FROM OLD.manifest_json
       OR NEW.config_schema_json IS DISTINCT FROM OLD.config_schema_json
       OR NEW.capabilities IS DISTINCT FROM OLD.capabilities
       OR NEW.permission_manifest IS DISTINCT FROM OLD.permission_manifest
       OR NEW.min_host_version IS DISTINCT FROM OLD.min_host_version
       OR NEW.created_by IS DISTINCT FROM OLD.created_by
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'connector_plugin_version artifact identity is immutable';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
        (OLD.status = 'IMPORTED' AND NEW.status IN ('VERIFIED', 'STAGING_FAILED'))
        OR (OLD.status = 'VERIFIED' AND NEW.status IN ('STAGING', 'STAGING_FAILED', 'DISABLED'))
        OR (OLD.status = 'STAGING' AND NEW.status IN ('ACTIVE', 'STAGING_FAILED'))
        OR (OLD.status = 'STAGING_FAILED' AND NEW.status IN ('VERIFIED', 'STAGING', 'DISABLED'))
        OR (OLD.status = 'ACTIVE' AND NEW.status IN ('VERIFIED', 'STAGING_FAILED', 'DISABLED'))
        OR (OLD.status = 'DISABLED' AND NEW.status IN ('VERIFIED', 'STAGING_FAILED'))
    ) THEN
        RAISE EXCEPTION 'connector_plugin_version status transition % -> % is not allowed',
            OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_vendor_connector_version_immutability()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'published vendor_connector_version is retained permanently';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.vendor_config_id IS DISTINCT FROM OLD.vendor_config_id
       OR NEW.created_by IS DISTINCT FROM OLD.created_by
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'vendor_connector_version identity is immutable';
    END IF;

    IF OLD.status = 'DRAFT' THEN
        IF NEW.status <> 'DRAFT'
           OR NEW.version_no IS DISTINCT FROM OLD.version_no
           OR NEW.snapshot_hash IS DISTINCT FROM OLD.snapshot_hash
           OR NEW.hash_algorithm IS DISTINCT FROM OLD.hash_algorithm
           OR NEW.integrity_hash IS DISTINCT FROM OLD.integrity_hash
           OR NEW.previous_version_id IS DISTINCT FROM OLD.previous_version_id
           OR NEW.published_at IS DISTINCT FROM OLD.published_at
           OR NEW.published_by IS DISTINCT FROM OLD.published_by THEN
            RAISE EXCEPTION 'DRAFT connector version cannot be promoted or acquire published facts';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.version_no IS DISTINCT FROM OLD.version_no
       OR NEW.draft_version IS DISTINCT FROM OLD.draft_version
       OR NEW.pipeline_snapshot IS DISTINCT FROM OLD.pipeline_snapshot
       OR NEW.snapshot_hash IS DISTINCT FROM OLD.snapshot_hash
       OR NEW.hash_algorithm IS DISTINCT FROM OLD.hash_algorithm
       OR NEW.integrity_hash IS DISTINCT FROM OLD.integrity_hash
       OR NEW.security_version IS DISTINCT FROM OLD.security_version
       OR NEW.previous_version_id IS DISTINCT FROM OLD.previous_version_id
       OR NEW.published_at IS DISTINCT FROM OLD.published_at
       OR NEW.published_by IS DISTINCT FROM OLD.published_by THEN
        RAISE EXCEPTION 'published vendor_connector_version facts are immutable';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status
       AND NOT (OLD.status = 'ACTIVE' AND NEW.status = 'SUPERSEDED') THEN
        RAISE EXCEPTION 'vendor_connector_version status transition % -> % is not allowed',
            OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

DROP INDEX idx_vendor_connector_test_product_gate_v049;
DROP INDEX idx_vendor_connector_authoring_mode_v049;
DROP INDEX idx_connector_plugin_product_catalog_v049;

ALTER TABLE vendor_connector_test_fact
    DROP CONSTRAINT ck_vendor_connector_test_authoring_v049,
    DROP COLUMN compile_hash,
    DROP COLUMN spec_hash,
    DROP COLUMN authoring_mode;

ALTER TABLE vendor_connector_version
    DROP CONSTRAINT ck_vendor_connector_product_spec_v049,
    DROP CONSTRAINT ck_vendor_connector_authoring_mode_v049,
    DROP COLUMN compile_hash,
    DROP COLUMN compiler_version,
    DROP COLUMN spec_hash,
    DROP COLUMN connector_spec,
    DROP COLUMN authoring_mode;

ALTER TABLE connector_plugin_version
    DROP CONSTRAINT ck_connector_plugin_manifest_binding_v049,
    DROP CONSTRAINT ck_connector_plugin_manifest_projection_v049,
    DROP COLUMN compatibility_manifest,
    DROP COLUMN output_mode,
    DROP COLUMN transport_mode,
    DROP COLUMN connector_kind,
    DROP COLUMN authoring_model,
    DROP COLUMN manifest_version;
