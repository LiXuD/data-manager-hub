-- Add connector product-authoring persistence without rewriting any existing runtime snapshot.
-- V047/V048 guards and the complete historical integrity matrix must already be present.

CREATE OR REPLACE FUNCTION v049_connector_canonical_jsonb(value JSONB)
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
                    to_json(key)::TEXT || ':' || v049_connector_canonical_jsonb(item),
                    ',' ORDER BY key), '') || '}'
            INTO canonical
            FROM jsonb_each(value) AS entry(key, item);
        WHEN 'array' THEN
            SELECT '[' || COALESCE(string_agg(
                    v049_connector_canonical_jsonb(item),
                    ',' ORDER BY ordinal), '') || ']'
            INTO canonical
            FROM jsonb_array_elements(value) WITH ORDINALITY AS entry(item, ordinal);
        ELSE
            canonical := value::TEXT;
    END CASE;
    RETURN canonical;
END;
$$;

CREATE OR REPLACE FUNCTION v049_connector_sha256(value JSONB)
RETURNS CHAR(64)
LANGUAGE SQL
IMMUTABLE
STRICT
AS $$
    SELECT encode(sha256(convert_to(v049_connector_canonical_jsonb(value), 'UTF8')), 'hex')::CHAR(64)
$$;

CREATE OR REPLACE FUNCTION v049_connector_v1_integrity(
    preserved_snapshot_hash TEXT,
    preserved_pipeline JSONB
)
RETURNS CHAR(64)
LANGUAGE SQL
STABLE
STRICT
AS $$
    SELECT v049_connector_sha256(jsonb_build_object(
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
                        'manifestHash', btrim(v049_connector_sha256(plugin.manifest_json)),
                        'schemaHash', btrim(v049_connector_sha256(plugin.config_schema_json))
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
    missing_dependencies TEXT;
    invalid_history TEXT;
BEGIN
    SELECT string_agg(required.name, ',' ORDER BY required.name)
    INTO missing_dependencies
    FROM (VALUES
        ('V047 changeset', EXISTS (
            SELECT 1 FROM databasechangelog
            WHERE id = 'enforce-connector-history-immutability-2026-08-10'
              AND author = 'data-platform')),
        ('V048 changeset', EXISTS (
            SELECT 1 FROM databasechangelog
            WHERE id = 'enforce-interface-vendor-routing-2026-08-11'
              AND author = 'data-platform')),
        ('V047 plugin trigger', EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgname = 'trg_connector_plugin_version_immutable' AND NOT tgisinternal)),
        ('V047 connector trigger', EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgname = 'trg_vendor_connector_version_immutable' AND NOT tgisinternal)),
        ('V048 routing trigger', EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgname = 'trg_api_interface_vendor_routing_v048' AND NOT tgisinternal)),
        ('V048 route protection', EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgname = 'trg_protect_referenced_vendor_config_routing_v048' AND NOT tgisinternal))
    ) AS required(name, present)
    WHERE NOT required.present;
    IF missing_dependencies IS NOT NULL THEN
        RAISE EXCEPTION 'V049 blocked: required V047/V048 history or objects are missing: %',
            missing_dependencies;
    END IF;

    SELECT string_agg(format('plugin:%s:%s@%s', id, plugin_id, version), ',' ORDER BY id)
    INTO invalid_history
    FROM connector_plugin_version
    WHERE manifest_json->>'pluginId' IS DISTINCT FROM plugin_id
       OR manifest_json->>'version' IS DISTINCT FROM version
       OR manifest_json->>'spiVersion' IS DISTINCT FROM spi_version
       OR manifest_json->>'entryClass' IS DISTINCT FROM entry_class
       OR manifest_json->>'minHostVersion' IS DISTINCT FROM min_host_version
       OR manifest_json->'configSchema' IS DISTINCT FROM config_schema_json
       OR manifest_json->'capabilities' IS DISTINCT FROM capabilities
       OR manifest_json->'permissions' IS DISTINCT FROM permission_manifest
       OR manifest_json->>'manifestVersion' IS DISTINCT FROM '1'
       OR artifact_sha256 !~ '^[0-9a-f]{64}$'
       OR btrim(detached_signature) = ''
       OR btrim(signing_key_id) = '';
    IF invalid_history IS NOT NULL THEN
        RAISE EXCEPTION 'V049 blocked: plugin history integrity drift: %', invalid_history;
    END IF;

    SELECT string_agg(format('connector:%s', version.id), ',' ORDER BY version.id)
    INTO invalid_history
    FROM vendor_connector_version version
    WHERE (version.status = 'DRAFT' AND (
                version.version_no IS NOT NULL
                OR version.snapshot_hash IS NOT NULL
                OR version.hash_algorithm IS NOT NULL
                OR version.integrity_hash IS NOT NULL))
       OR (version.status <> 'DRAFT' AND (
                version.version_no IS NULL
                OR jsonb_typeof(version.pipeline_snapshot) IS DISTINCT FROM 'array'
                OR jsonb_array_length(version.pipeline_snapshot) = 0
                OR version.snapshot_hash !~ '^[0-9a-f]{64}$'
                OR version.hash_algorithm NOT IN ('V1_DERIVED', 'V2_EMBEDDED')
                OR version.integrity_hash !~ '^[0-9a-f]{64}$'
                OR lower(btrim(version.snapshot_hash)) IS DISTINCT FROM
                    btrim(v049_connector_sha256(version.pipeline_snapshot))
                OR (version.hash_algorithm = 'V2_EMBEDDED'
                    AND lower(btrim(version.integrity_hash)) IS DISTINCT FROM
                        lower(btrim(version.snapshot_hash)))
                OR EXISTS (
                    SELECT 1
                    FROM jsonb_array_elements(version.pipeline_snapshot) AS step(item)
                    LEFT JOIN connector_plugin_version plugin
                      ON plugin.plugin_id = step.item->>'pluginId'
                     AND plugin.version = step.item->>'pluginVersion'
                    WHERE plugin.id IS NULL
                       OR step.item->>'configHash' IS NULL
                       OR lower(step.item->>'configHash') IS DISTINCT FROM
                          btrim(v049_connector_sha256(COALESCE(step.item->'config', '{}'::JSONB)))
                       OR (version.hash_algorithm = 'V2_EMBEDDED' AND (
                              lower(step.item->>'artifactSha256') IS DISTINCT FROM
                                  lower(btrim(plugin.artifact_sha256))
                              OR lower(step.item->>'manifestHash') IS DISTINCT FROM
                                  btrim(v049_connector_sha256(plugin.manifest_json))
                              OR lower(step.item->>'schemaHash') IS DISTINCT FROM
                                  btrim(v049_connector_sha256(plugin.config_schema_json)))))))
       OR (version.status <> 'DRAFT' AND version.hash_algorithm = 'V1_DERIVED'
           AND lower(btrim(version.integrity_hash)) IS DISTINCT FROM btrim(
               v049_connector_v1_integrity(version.snapshot_hash, version.pipeline_snapshot)));
    IF invalid_history IS NOT NULL THEN
        RAISE EXCEPTION 'V049 blocked: connector history integrity drift: %', invalid_history;
    END IF;

    SELECT string_agg(format('test:%s', id), ',' ORDER BY id)
    INTO invalid_history
    FROM vendor_connector_test_fact
    WHERE draft_version <= 0
       OR snapshot_hash !~ '^[0-9a-f]{64}$'
       OR result_digest !~ '^[0-9a-f]{64}$'
       OR jsonb_typeof(plugin_bindings) IS DISTINCT FROM 'array';
    IF invalid_history IS NOT NULL THEN
        RAISE EXCEPTION 'V049 blocked: connector test fact integrity drift: %', invalid_history;
    END IF;

    SELECT string_agg(format('call:%s', id), ',' ORDER BY id)
    INTO invalid_history
    FROM call_record
    WHERE (plugin_id IS NULL) <> (plugin_version IS NULL)
       OR plugin_id = '' OR plugin_version = ''
       OR (plugin_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM connector_plugin_version plugin
            WHERE plugin.plugin_id = call_record.plugin_id
              AND plugin.version = call_record.plugin_version))
       OR (hash_algorithm IS NULL) <> (integrity_hash IS NULL)
       OR (hash_algorithm IS NOT NULL AND (
            hash_algorithm NOT IN ('V1_DERIVED', 'V2_EMBEDDED')
            OR snapshot_hash !~ '^[0-9a-f]{64}$'
            OR integrity_hash !~ '^[0-9a-f]{64}$'
            OR (hash_algorithm = 'V2_EMBEDDED' AND integrity_hash <> snapshot_hash)));
    IF invalid_history IS NOT NULL THEN
        RAISE EXCEPTION 'V049 blocked: Access connector history integrity drift: %', invalid_history;
    END IF;

    SELECT string_agg(format('billing:%s', id), ',' ORDER BY id)
    INTO invalid_history
    FROM billing_event
    WHERE (plugin_id IS NULL) <> (plugin_version IS NULL)
       OR plugin_id = '' OR plugin_version = ''
       OR (plugin_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM connector_plugin_version plugin
            WHERE plugin.plugin_id = billing_event.plugin_id
              AND plugin.version = billing_event.plugin_version))
       OR (hash_algorithm IS NULL) <> (integrity_hash IS NULL)
       OR (hash_algorithm IS NOT NULL AND (
            hash_algorithm NOT IN ('V1_DERIVED', 'V2_EMBEDDED')
            OR snapshot_hash !~ '^[0-9a-f]{64}$'
            OR integrity_hash !~ '^[0-9a-f]{64}$'
            OR (hash_algorithm = 'V2_EMBEDDED' AND integrity_hash <> snapshot_hash)));
    IF invalid_history IS NOT NULL THEN
        RAISE EXCEPTION 'V049 blocked: Billing connector history integrity drift: %', invalid_history;
    END IF;
END;
$$;

ALTER TABLE connector_plugin_version
    ADD COLUMN manifest_version VARCHAR(16),
    ADD COLUMN authoring_model VARCHAR(32),
    ADD COLUMN connector_kind VARCHAR(32),
    ADD COLUMN transport_mode VARCHAR(32),
    ADD COLUMN output_mode VARCHAR(32),
    ADD COLUMN compatibility_manifest JSONB;

ALTER TABLE vendor_connector_version
    ADD COLUMN authoring_mode VARCHAR(32),
    ADD COLUMN connector_spec JSONB,
    ADD COLUMN spec_hash CHAR(64),
    ADD COLUMN compiler_version VARCHAR(32),
    ADD COLUMN compile_hash CHAR(64);

ALTER TABLE vendor_connector_test_fact
    ADD COLUMN authoring_mode VARCHAR(32),
    ADD COLUMN spec_hash CHAR(64),
    ADD COLUMN compile_hash CHAR(64);

UPDATE connector_plugin_version
SET manifest_version = '1',
    authoring_model = 'ADVANCED_PIPELINE';

UPDATE vendor_connector_version
SET authoring_mode = 'ADVANCED_LEGACY';

-- The V042 fact table rejects every UPDATE. Temporarily disable only that one
-- immutable trigger while backfilling the newly-added projection, then restore
-- it in the same migration transaction. Any failure rolls the whole operation back.
ALTER TABLE vendor_connector_test_fact
    DISABLE TRIGGER trg_vendor_connector_test_fact_immutable;
UPDATE vendor_connector_test_fact
SET authoring_mode = 'ADVANCED_LEGACY';
ALTER TABLE vendor_connector_test_fact
    ENABLE TRIGGER trg_vendor_connector_test_fact_immutable;

ALTER TABLE connector_plugin_version
    ALTER COLUMN manifest_version SET NOT NULL,
    ALTER COLUMN authoring_model SET NOT NULL,
    ADD CONSTRAINT ck_connector_plugin_manifest_projection_v049 CHECK (
        (manifest_version = '1'
         AND authoring_model = 'ADVANCED_PIPELINE'
         AND connector_kind IS NULL
         AND transport_mode IS NULL
         AND output_mode IS NULL
         AND compatibility_manifest IS NULL)
        OR
        (manifest_version = '2'
         AND authoring_model IN ('SIMPLE_CONNECTOR', 'ADVANCED_PIPELINE')
         AND connector_kind IS NOT NULL
         AND connector_kind IN ('DEDICATED_VENDOR', 'GENERIC_HTTP')
         AND transport_mode IS NOT NULL
         AND transport_mode IN ('HOST_SINGLE_HTTP', 'HOST_MANAGED_MULTI_HTTP')
         AND output_mode IS NOT NULL
         AND output_mode IN ('PLUGIN_NORMALIZED', 'HOST_MAPPING')
         AND compatibility_manifest IS NOT NULL
         AND jsonb_typeof(compatibility_manifest) = 'object'
         AND compatibility_manifest <> '{}'::JSONB)
    ),
    ADD CONSTRAINT ck_connector_plugin_manifest_binding_v049 CHECK (
        (manifest_json->>'manifestVersion') IS NOT DISTINCT FROM manifest_version
        AND (manifest_version <> '2' OR (
            (manifest_json->>'authoringModel') IS NOT DISTINCT FROM authoring_model
            AND (manifest_json->>'connectorKind') IS NOT DISTINCT FROM connector_kind
            AND (manifest_json->>'transportMode') IS NOT DISTINCT FROM transport_mode
            AND (manifest_json->>'outputMode') IS NOT DISTINCT FROM output_mode
            AND (manifest_json->'compatibility') IS NOT DISTINCT FROM compatibility_manifest))
    );

ALTER TABLE vendor_connector_version
    ALTER COLUMN authoring_mode SET NOT NULL,
    ADD CONSTRAINT ck_vendor_connector_authoring_mode_v049 CHECK (
        authoring_mode IN ('SIMPLE_CONNECTOR', 'ADVANCED_LEGACY')),
    ADD CONSTRAINT ck_vendor_connector_product_spec_v049 CHECK (
        (authoring_mode = 'ADVANCED_LEGACY'
         AND connector_spec IS NULL
         AND spec_hash IS NULL
         AND compiler_version IS NULL
         AND compile_hash IS NULL)
        OR
        (authoring_mode = 'SIMPLE_CONNECTOR'
         AND connector_spec IS NOT NULL
         AND jsonb_typeof(connector_spec) = 'object'
         AND octet_length(convert_to(connector_spec::TEXT, 'UTF8')) <= 131072
         AND spec_hash IS NOT NULL
         AND spec_hash ~ '^[0-9a-f]{64}$'
         AND compiler_version IS NOT NULL
         AND compiler_version ~ '^[0-9]+[.][0-9]+([.][0-9]+)?$'
         AND compile_hash IS NOT NULL
         AND compile_hash ~ '^[0-9a-f]{64}$'
         AND jsonb_typeof(pipeline_snapshot) = 'array'
         AND jsonb_array_length(pipeline_snapshot) > 0
         AND ((status = 'DRAFT' AND snapshot_hash IS NULL)
              OR (status <> 'DRAFT'
                  AND snapshot_hash ~ '^[0-9a-f]{64}$'
                  AND hash_algorithm IS NOT NULL
                  AND integrity_hash IS NOT NULL)))
    );

ALTER TABLE vendor_connector_test_fact
    ALTER COLUMN authoring_mode SET NOT NULL,
    ADD CONSTRAINT ck_vendor_connector_test_authoring_v049 CHECK (
        (authoring_mode = 'ADVANCED_LEGACY'
         AND spec_hash IS NULL
         AND compile_hash IS NULL)
        OR
        (authoring_mode = 'SIMPLE_CONNECTOR'
         AND spec_hash IS NOT NULL
         AND spec_hash ~ '^[0-9a-f]{64}$'
         AND compile_hash IS NOT NULL
         AND compile_hash ~ '^[0-9a-f]{64}$')
    );

CREATE INDEX idx_connector_plugin_product_catalog_v049
    ON connector_plugin_version(manifest_version, authoring_model, connector_kind, status);
CREATE INDEX idx_vendor_connector_authoring_mode_v049
    ON vendor_connector_version(vendor_config_id, authoring_mode, status);
CREATE INDEX idx_vendor_connector_test_product_gate_v049
    ON vendor_connector_test_fact(
        vendor_config_id, draft_version, spec_hash, snapshot_hash, compile_hash)
    WHERE test_succeeded = TRUE AND authoring_mode = 'SIMPLE_CONNECTOR';

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
       OR NEW.manifest_version IS DISTINCT FROM OLD.manifest_version
       OR NEW.authoring_model IS DISTINCT FROM OLD.authoring_model
       OR NEW.connector_kind IS DISTINCT FROM OLD.connector_kind
       OR NEW.transport_mode IS DISTINCT FROM OLD.transport_mode
       OR NEW.output_mode IS DISTINCT FROM OLD.output_mode
       OR NEW.compatibility_manifest IS DISTINCT FROM OLD.compatibility_manifest
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
       OR NEW.published_by IS DISTINCT FROM OLD.published_by
       OR NEW.authoring_mode IS DISTINCT FROM OLD.authoring_mode
       OR NEW.connector_spec IS DISTINCT FROM OLD.connector_spec
       OR NEW.spec_hash IS DISTINCT FROM OLD.spec_hash
       OR NEW.compiler_version IS DISTINCT FROM OLD.compiler_version
       OR NEW.compile_hash IS DISTINCT FROM OLD.compile_hash THEN
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

COMMENT ON COLUMN connector_plugin_version.compatibility_manifest IS
    'Immutable index projection of the signed Manifest v2 compatibility object';
COMMENT ON COLUMN vendor_connector_version.connector_spec IS
    'Immutable SIMPLE product configuration; null for ADVANCED_LEGACY';
COMMENT ON COLUMN vendor_connector_version.compile_hash IS
    'Digest binding SIMPLE connector_spec to its compiled pipeline snapshot';
COMMENT ON COLUMN vendor_connector_test_fact.compile_hash IS
    'SIMPLE controlled-test compile binding; null for ADVANCED_LEGACY';

DROP FUNCTION v049_connector_v1_integrity(TEXT, JSONB);
DROP FUNCTION v049_connector_sha256(JSONB);
DROP FUNCTION v049_connector_canonical_jsonb(JSONB);
