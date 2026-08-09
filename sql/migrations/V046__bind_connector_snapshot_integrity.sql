-- Add versioned integrity facts without rewriting any historical pipeline_snapshot or snapshot_hash.

ALTER TABLE vendor_connector_version
    ADD COLUMN IF NOT EXISTS hash_algorithm VARCHAR(32),
    ADD COLUMN IF NOT EXISTS integrity_hash CHAR(64);

ALTER TABLE call_record
    ADD COLUMN IF NOT EXISTS hash_algorithm VARCHAR(32),
    ADD COLUMN IF NOT EXISTS integrity_hash CHAR(64);

ALTER TABLE billing_event
    ADD COLUMN IF NOT EXISTS hash_algorithm VARCHAR(32),
    ADD COLUMN IF NOT EXISTS integrity_hash CHAR(64);

DO $$
DECLARE
    missing_bindings TEXT;
BEGIN
    SELECT string_agg(format('%s:%s@%s', version.id,
                             step.item->>'pluginId', step.item->>'pluginVersion'),
                      ',' ORDER BY version.id)
    INTO missing_bindings
    FROM vendor_connector_version version
    CROSS JOIN LATERAL jsonb_array_elements(version.pipeline_snapshot) AS step(item)
    LEFT JOIN connector_plugin_version plugin
      ON plugin.plugin_id = step.item->>'pluginId'
     AND plugin.version = step.item->>'pluginVersion'
    WHERE version.status <> 'DRAFT'
      AND version.hash_algorithm IS NULL
      AND plugin.id IS NULL;

    IF missing_bindings IS NOT NULL THEN
        RAISE EXCEPTION 'V046 cannot derive immutable connector integrity; missing bindings: %',
            missing_bindings;
    END IF;
END $$;

CREATE OR REPLACE FUNCTION v046_connector_canonical_jsonb(value JSONB)
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
                    to_json(key)::TEXT || ':' || v046_connector_canonical_jsonb(item),
                    ',' ORDER BY key), '') || '}'
            INTO canonical
            FROM jsonb_each(value) AS entry(key, item);
        WHEN 'array' THEN
            SELECT '[' || COALESCE(string_agg(
                    v046_connector_canonical_jsonb(item),
                    ',' ORDER BY ordinal), '') || ']'
            INTO canonical
            FROM jsonb_array_elements(value) WITH ORDINALITY AS entry(item, ordinal);
        ELSE
            canonical := value::TEXT;
    END CASE;
    RETURN canonical;
END;
$$;

CREATE OR REPLACE FUNCTION v046_connector_sha256(value JSONB)
RETURNS CHAR(64)
LANGUAGE SQL
IMMUTABLE
STRICT
AS $$
    SELECT encode(sha256(convert_to(v046_connector_canonical_jsonb(value), 'UTF8')), 'hex')::CHAR(64)
$$;

CREATE OR REPLACE FUNCTION v046_connector_v1_integrity(
    preserved_snapshot_hash TEXT,
    preserved_pipeline JSONB
)
RETURNS CHAR(64)
LANGUAGE SQL
STABLE
STRICT
AS $$
    SELECT v046_connector_sha256(jsonb_build_object(
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
                        'manifestHash', btrim(v046_connector_sha256(plugin.manifest_json)),
                        'schemaHash', btrim(v046_connector_sha256(plugin.config_schema_json))
                    ) AS metadata
                FROM jsonb_array_elements(preserved_pipeline) AS step(item)
                JOIN connector_plugin_version plugin
                  ON plugin.plugin_id = step.item->>'pluginId'
                 AND plugin.version = step.item->>'pluginVersion'
            ) binding
        ), '[]'::JSONB)
    ));
$$;

UPDATE vendor_connector_version
SET hash_algorithm = 'V1_DERIVED',
    integrity_hash = v046_connector_v1_integrity(snapshot_hash, pipeline_snapshot)
WHERE status <> 'DRAFT'
  AND hash_algorithm IS NULL;

-- Populate new nullable trace columns only when an immutable historical version can be identified.
-- Existing business facts and their preserved snapshot_hash values are never changed.
UPDATE call_record record
SET hash_algorithm = matched.hash_algorithm,
    integrity_hash = matched.integrity_hash
FROM (
    SELECT DISTINCT config.vendor_id, version.version_no, version.snapshot_hash,
           version.hash_algorithm, version.integrity_hash
    FROM vendor_connector_version version
    JOIN vendor_config config ON config.id = version.vendor_config_id
    WHERE version.status <> 'DRAFT'
) matched
WHERE record.hash_algorithm IS NULL
  AND record.vendor_id = matched.vendor_id
  AND record.pipeline_version = matched.version_no
  AND record.snapshot_hash = matched.snapshot_hash;

UPDATE billing_event event
SET hash_algorithm = matched.hash_algorithm,
    integrity_hash = matched.integrity_hash
FROM (
    SELECT DISTINCT config.vendor_id, version.version_no, version.snapshot_hash,
           version.hash_algorithm, version.integrity_hash
    FROM vendor_connector_version version
    JOIN vendor_config config ON config.id = version.vendor_config_id
    WHERE version.status <> 'DRAFT'
) matched
WHERE event.hash_algorithm IS NULL
  AND event.vendor_id = matched.vendor_id
  AND event.pipeline_version = matched.version_no
  AND event.snapshot_hash = matched.snapshot_hash;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_vendor_connector_integrity') THEN
        ALTER TABLE vendor_connector_version
            ADD CONSTRAINT ck_vendor_connector_integrity CHECK (
                (status = 'DRAFT' AND hash_algorithm IS NULL AND integrity_hash IS NULL)
                OR (status <> 'DRAFT'
                    AND hash_algorithm IN ('V1_DERIVED', 'V2_EMBEDDED')
                    AND integrity_hash ~ '^[0-9a-f]{64}$'
                    AND (hash_algorithm <> 'V2_EMBEDDED' OR integrity_hash = snapshot_hash))
            );
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_call_record_connector_integrity') THEN
        ALTER TABLE call_record
            ADD CONSTRAINT ck_call_record_connector_integrity CHECK (
                hash_algorithm IS NULL
                OR (hash_algorithm IN ('V1_DERIVED', 'V2_EMBEDDED')
                    AND integrity_hash ~ '^[0-9a-f]{64}$'
                    AND (hash_algorithm <> 'V2_EMBEDDED' OR integrity_hash = snapshot_hash))
            );
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_billing_event_connector_integrity') THEN
        ALTER TABLE billing_event
            ADD CONSTRAINT ck_billing_event_connector_integrity CHECK (
                hash_algorithm IS NULL
                OR (hash_algorithm IN ('V1_DERIVED', 'V2_EMBEDDED')
                    AND integrity_hash ~ '^[0-9a-f]{64}$'
                    AND (hash_algorithm <> 'V2_EMBEDDED' OR integrity_hash = snapshot_hash))
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_call_record_connector_integrity
    ON call_record(hash_algorithm, integrity_hash, call_time)
    WHERE integrity_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_billing_event_connector_integrity
    ON billing_event(hash_algorithm, integrity_hash, call_time)
    WHERE integrity_hash IS NOT NULL;

COMMENT ON COLUMN vendor_connector_version.hash_algorithm IS
    'V1_DERIVED preserves historical snapshot_hash; V2_EMBEDDED binds config, Manifest, Schema and artifact';
COMMENT ON COLUMN vendor_connector_version.integrity_hash IS
    'Versioned immutable connector integrity fact; never replaces historical snapshot_hash';
COMMENT ON COLUMN call_record.integrity_hash IS 'Actual connector integrity fact observed by Access';
COMMENT ON COLUMN billing_event.integrity_hash IS 'Connector integrity fact used for this billing decision';

DROP FUNCTION v046_connector_v1_integrity(TEXT, JSONB);
DROP FUNCTION v046_connector_sha256(JSONB);
DROP FUNCTION v046_connector_canonical_jsonb(JSONB);
