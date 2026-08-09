-- Freeze connector artifacts and published runtime facts without creating cross-domain foreign keys.
-- Every pre-existing inconsistency aborts this transactional changeset; no historical fact is rewritten.

CREATE OR REPLACE FUNCTION v047_connector_canonical_jsonb(value JSONB)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    canonical TEXT;
BEGIN
    CASE jsonb_typeof(value)
        WHEN 'object' THEN
            SELECT '{' || COALESCE(string_agg(
                    to_json(key)::TEXT || ':' || v047_connector_canonical_jsonb(item),
                    ',' ORDER BY key), '') || '}'
            INTO canonical
            FROM jsonb_each(value) AS entry(key, item);
        WHEN 'array' THEN
            SELECT '[' || COALESCE(string_agg(
                    v047_connector_canonical_jsonb(item),
                    ',' ORDER BY ordinal), '') || ']'
            INTO canonical
            FROM jsonb_array_elements(value) WITH ORDINALITY AS entry(item, ordinal);
        ELSE
            canonical := value::TEXT;
    END CASE;
    RETURN canonical;
END;
$$;

CREATE OR REPLACE FUNCTION v047_connector_sha256(value JSONB)
RETURNS CHAR(64)
LANGUAGE SQL
IMMUTABLE
STRICT
AS $$
    SELECT encode(sha256(convert_to(v047_connector_canonical_jsonb(value), 'UTF8')), 'hex')::CHAR(64)
$$;

CREATE OR REPLACE FUNCTION v047_connector_v1_integrity(
    preserved_snapshot_hash TEXT,
    preserved_pipeline JSONB
)
RETURNS CHAR(64)
LANGUAGE SQL
STABLE
STRICT
AS $$
    SELECT v047_connector_sha256(jsonb_build_object(
        'algorithm', 'V1_DERIVED',
        'snapshotHash', btrim(preserved_snapshot_hash),
        'plugins', COALESCE((
            SELECT jsonb_agg(binding.metadata ORDER BY binding.plugin_id, binding.plugin_version)
            FROM (
                SELECT DISTINCT
                    plugin.plugin_id,
                    plugin.version AS plugin_version,
                    jsonb_build_object(
                        'pluginId', plugin.plugin_id,
                        'version', plugin.version,
                        'artifactSha256', lower(btrim(plugin.artifact_sha256)),
                        'manifestHash', btrim(v047_connector_sha256(plugin.manifest_json)),
                        'schemaHash', btrim(v047_connector_sha256(plugin.config_schema_json))
                    ) AS metadata
                FROM jsonb_array_elements(preserved_pipeline) AS step(item)
                JOIN connector_plugin_version plugin
                  ON plugin.plugin_id = step.item->>'pluginId'
                 AND plugin.version = step.item->>'pluginVersion'
            ) binding
        ), '[]'::JSONB)
    ));
$$;

DO $$
DECLARE
    invalid_catalog TEXT;
BEGIN
    SELECT string_agg(format('%s:%s@%s', id, plugin_id, version), ',' ORDER BY id)
    INTO invalid_catalog
    FROM connector_plugin_version
    WHERE manifest_json->>'pluginId' IS DISTINCT FROM plugin_id
       OR manifest_json->>'version' IS DISTINCT FROM version
       OR manifest_json->>'spiVersion' IS DISTINCT FROM spi_version
       OR manifest_json->>'entryClass' IS DISTINCT FROM entry_class
       OR manifest_json->>'minHostVersion' IS DISTINCT FROM min_host_version
       OR manifest_json->'configSchema' IS DISTINCT FROM config_schema_json
       OR manifest_json->'capabilities' IS DISTINCT FROM capabilities
       OR manifest_json->'permissions' IS DISTINCT FROM permission_manifest
       OR artifact_sha256 !~ '^[0-9a-f]{64}$'
       OR btrim(detached_signature) = ''
       OR btrim(signing_key_id) = '';

    IF invalid_catalog IS NOT NULL THEN
        RAISE EXCEPTION 'V047 refuses to freeze inconsistent plugin catalog rows: %', invalid_catalog;
    END IF;
END $$;

DO $$
DECLARE
    invalid_shape TEXT;
BEGIN
    SELECT string_agg(id::TEXT, ',' ORDER BY id)
    INTO invalid_shape
    FROM vendor_connector_version
    WHERE status <> 'DRAFT'
      AND (jsonb_typeof(pipeline_snapshot) IS DISTINCT FROM 'array'
           OR jsonb_array_length(pipeline_snapshot) = 0);

    IF invalid_shape IS NOT NULL THEN
        RAISE EXCEPTION 'V047 refuses invalid published connector pipeline shapes: %', invalid_shape;
    END IF;
END $$;

DO $$
DECLARE
    invalid_versions TEXT;
BEGIN
    SELECT string_agg(version.id::TEXT, ',' ORDER BY version.id)
    INTO invalid_versions
    FROM vendor_connector_version version
    WHERE version.status <> 'DRAFT'
      AND (
          version.hash_algorithm NOT IN ('V1_DERIVED', 'V2_EMBEDDED')
          OR version.snapshot_hash !~ '^[0-9a-f]{64}$'
          OR version.integrity_hash !~ '^[0-9a-f]{64}$'
          OR lower(btrim(version.snapshot_hash))
                IS DISTINCT FROM btrim(v047_connector_sha256(version.pipeline_snapshot))
          OR (version.hash_algorithm = 'V1_DERIVED'
              AND lower(btrim(version.integrity_hash)) IS DISTINCT FROM
                  btrim(v047_connector_v1_integrity(version.snapshot_hash, version.pipeline_snapshot)))
          OR (version.hash_algorithm = 'V2_EMBEDDED'
              AND lower(btrim(version.integrity_hash)) IS DISTINCT FROM lower(btrim(version.snapshot_hash)))
          OR EXISTS (
              SELECT 1
              FROM jsonb_array_elements(version.pipeline_snapshot) AS step(item)
              LEFT JOIN connector_plugin_version plugin
                ON plugin.plugin_id = step.item->>'pluginId'
               AND plugin.version = step.item->>'pluginVersion'
              WHERE plugin.id IS NULL
                 OR step.item->>'configHash' IS NULL
                 OR lower(step.item->>'configHash') IS DISTINCT FROM
                    btrim(v047_connector_sha256(COALESCE(step.item->'config', '{}'::JSONB)))
                 OR (version.hash_algorithm = 'V2_EMBEDDED' AND (
                        lower(step.item->>'artifactSha256') IS DISTINCT FROM lower(btrim(plugin.artifact_sha256))
                        OR lower(step.item->>'manifestHash') IS DISTINCT FROM
                            btrim(v047_connector_sha256(plugin.manifest_json))
                        OR lower(step.item->>'schemaHash') IS DISTINCT FROM
                            btrim(v047_connector_sha256(plugin.config_schema_json))))
          )
      );

    IF invalid_versions IS NOT NULL THEN
        RAISE EXCEPTION 'V047 refuses inconsistent published connector versions: %', invalid_versions;
    END IF;
END $$;

DO $$
DECLARE
    invalid_calls TEXT;
    invalid_billing TEXT;
BEGIN
    SELECT string_agg(record.id::TEXT, ',' ORDER BY record.id)
    INTO invalid_calls
    FROM call_record record
    LEFT JOIN connector_plugin_version plugin
      ON plugin.plugin_id = record.plugin_id
     AND plugin.version = record.plugin_version
    WHERE (record.plugin_id IS NULL) <> (record.plugin_version IS NULL)
       OR record.plugin_id = '' OR record.plugin_version = ''
       OR (record.plugin_id IS NOT NULL AND plugin.id IS NULL)
       OR (record.hash_algorithm IS NULL) <> (record.integrity_hash IS NULL)
       OR (record.hash_algorithm IS NOT NULL AND (
            record.hash_algorithm NOT IN ('V1_DERIVED', 'V2_EMBEDDED')
            OR record.integrity_hash !~ '^[0-9a-f]{64}$'
            OR record.snapshot_hash !~ '^[0-9a-f]{64}$'
            OR (record.hash_algorithm = 'V2_EMBEDDED'
                AND lower(btrim(record.integrity_hash)) IS DISTINCT FROM lower(btrim(record.snapshot_hash)))));

    IF invalid_calls IS NOT NULL THEN
        RAISE EXCEPTION 'V047 refuses inconsistent Access connector history: %', invalid_calls;
    END IF;

    SELECT string_agg(event.id::TEXT, ',' ORDER BY event.id)
    INTO invalid_billing
    FROM billing_event event
    LEFT JOIN connector_plugin_version plugin
      ON plugin.plugin_id = event.plugin_id
     AND plugin.version = event.plugin_version
    WHERE (event.plugin_id IS NULL) <> (event.plugin_version IS NULL)
       OR event.plugin_id = '' OR event.plugin_version = ''
       OR (event.plugin_id IS NOT NULL AND plugin.id IS NULL)
       OR (event.hash_algorithm IS NULL) <> (event.integrity_hash IS NULL)
       OR (event.hash_algorithm IS NOT NULL AND (
            event.hash_algorithm NOT IN ('V1_DERIVED', 'V2_EMBEDDED')
            OR event.integrity_hash !~ '^[0-9a-f]{64}$'
            OR event.snapshot_hash !~ '^[0-9a-f]{64}$'
            OR (event.hash_algorithm = 'V2_EMBEDDED'
                AND lower(btrim(event.integrity_hash)) IS DISTINCT FROM lower(btrim(event.snapshot_hash)))));

    IF invalid_billing IS NOT NULL THEN
        RAISE EXCEPTION 'V047 refuses inconsistent Billing connector history: %', invalid_billing;
    END IF;
END $$;

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

DROP TRIGGER IF EXISTS trg_connector_plugin_version_immutable ON connector_plugin_version;
CREATE TRIGGER trg_connector_plugin_version_immutable
    BEFORE UPDATE OR DELETE ON connector_plugin_version
    FOR EACH ROW EXECUTE FUNCTION reject_connector_plugin_version_identity_mutation();

CREATE OR REPLACE FUNCTION reject_connector_plugin_physical_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'connector_plugin is retained permanently; use DISABLED status';
END;
$$;

DROP TRIGGER IF EXISTS trg_connector_plugin_reject_delete ON connector_plugin;
CREATE TRIGGER trg_connector_plugin_reject_delete
    BEFORE DELETE ON connector_plugin
    FOR EACH ROW EXECUTE FUNCTION reject_connector_plugin_physical_delete();

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

DROP TRIGGER IF EXISTS trg_vendor_connector_version_immutable ON vendor_connector_version;
CREATE TRIGGER trg_vendor_connector_version_immutable
    BEFORE UPDATE OR DELETE ON vendor_connector_version
    FOR EACH ROW EXECUTE FUNCTION enforce_vendor_connector_version_immutability();

ALTER TABLE call_record
    ADD CONSTRAINT ck_call_record_connector_reference_pair_v047 CHECK (
        (plugin_id IS NULL AND plugin_version IS NULL)
        OR (plugin_id IS NOT NULL AND plugin_version IS NOT NULL
            AND btrim(plugin_id) <> '' AND btrim(plugin_version) <> '')
    ),
    ADD CONSTRAINT ck_call_record_connector_integrity_pair_v047 CHECK (
        (hash_algorithm IS NULL AND integrity_hash IS NULL)
        OR (hash_algorithm IS NOT NULL AND integrity_hash IS NOT NULL AND snapshot_hash IS NOT NULL
            AND hash_algorithm IN ('V1_DERIVED', 'V2_EMBEDDED')
            AND integrity_hash ~ '^[0-9a-f]{64}$'
            AND snapshot_hash ~ '^[0-9a-f]{64}$'
            AND (hash_algorithm <> 'V2_EMBEDDED' OR integrity_hash = snapshot_hash))
    );

ALTER TABLE billing_event
    ADD CONSTRAINT ck_billing_event_connector_reference_pair_v047 CHECK (
        (plugin_id IS NULL AND plugin_version IS NULL)
        OR (plugin_id IS NOT NULL AND plugin_version IS NOT NULL
            AND btrim(plugin_id) <> '' AND btrim(plugin_version) <> '')
    ),
    ADD CONSTRAINT ck_billing_event_connector_integrity_pair_v047 CHECK (
        (hash_algorithm IS NULL AND integrity_hash IS NULL)
        OR (hash_algorithm IS NOT NULL AND integrity_hash IS NOT NULL AND snapshot_hash IS NOT NULL
            AND hash_algorithm IN ('V1_DERIVED', 'V2_EMBEDDED')
            AND integrity_hash ~ '^[0-9a-f]{64}$'
            AND snapshot_hash ~ '^[0-9a-f]{64}$'
            AND (hash_algorithm <> 'V2_EMBEDDED' OR integrity_hash = snapshot_hash))
    );

COMMENT ON TRIGGER trg_connector_plugin_version_immutable ON connector_plugin_version IS
    'Artifact identity is immutable; only controlled state, verification, safe error and audit facts may change';
COMMENT ON TRIGGER trg_vendor_connector_version_immutable ON vendor_connector_version IS
    'DRAFT remains editable; published snapshots and publication facts are permanently immutable';
COMMENT ON CONSTRAINT ck_call_record_connector_reference_pair_v047 ON call_record IS
    'Access owns call history; catalog deletion protection preserves interpretation without a cross-domain FK';
COMMENT ON CONSTRAINT ck_billing_event_connector_reference_pair_v047 ON billing_event IS
    'Billing owns ledger history; catalog deletion protection preserves interpretation without a cross-domain FK';

DROP FUNCTION v047_connector_v1_integrity(TEXT, JSONB);
DROP FUNCTION v047_connector_sha256(JSONB);
DROP FUNCTION v047_connector_canonical_jsonb(JSONB);
