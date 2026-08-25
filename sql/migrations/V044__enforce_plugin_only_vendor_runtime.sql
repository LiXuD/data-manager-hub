-- Retire LEGACY vendor execution while preserving the two repository-owned UAPI seed flows.
-- The change is intentionally fail-closed: non-seed active configurations must have completed
-- the controlled Stage-4 migration before this migration is allowed to commit.

CREATE OR REPLACE FUNCTION v044_connector_canonical_jsonb(value JSONB)
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
                    to_json(key)::TEXT || ':' || v044_connector_canonical_jsonb(item),
                    ',' ORDER BY key), '') || '}'
            INTO canonical
            FROM jsonb_each(value) AS entry(key, item);
        WHEN 'array' THEN
            SELECT '[' || COALESCE(string_agg(
                    v044_connector_canonical_jsonb(item),
                    ',' ORDER BY ordinal), '') || ']'
            INTO canonical
            FROM jsonb_array_elements(value) WITH ORDINALITY AS entry(item, ordinal);
        ELSE
            canonical := value::TEXT;
    END CASE;
    RETURN canonical;
END;
$$;

CREATE OR REPLACE FUNCTION v044_connector_sha256(value JSONB)
RETURNS CHAR(64)
LANGUAGE SQL
IMMUTABLE
STRICT
AS $$
    SELECT encode(sha256(convert_to(v044_connector_canonical_jsonb(value), 'UTF8')), 'hex')::CHAR(64)
$$;

-- Only untouched, repository-owned seed configurations may bypass the operational observation gate.
-- Any drift makes the row ineligible and the general active-LEGACY gate below aborts the migration.
WITH seed_source AS (
    SELECT vc.id AS vendor_config_id,
           vc.security_version,
           ai.interface_code,
           vc.api_url,
           jsonb_build_object(
               'apiUrl', vc.api_url,
               'method', 'GET',
               'requestMapping', '{}'::JSONB,
               'headers', '{}'::JSONB,
               'contentType', 'application/json; charset=utf-8',
               'connectTimeoutMs', 10000,
               'readTimeoutMs', 10000,
               'totalTimeoutMs', 10000,
               'idempotencyPolicy', 'IDEMPOTENT',
               'maxResponseBytes', 10485760
           ) AS builder_config,
           jsonb_build_object(
               'authType', 'NONE',
               'authConfig', '{}'::JSONB,
               'securitySteps', '[]'::JSONB,
               'secretRefs', '{}'::JSONB
           ) AS request_processor_config,
           jsonb_build_object(
               'securitySteps', '[]'::JSONB,
               'secretRefs', '{}'::JSONB
           ) AS response_processor_config,
           jsonb_build_object('responseMapping', '{}'::JSONB) AS normalizer_config
    FROM vendor_config vc
    JOIN vendor_info vi ON vi.id = vc.vendor_id
    JOIN data_type dt ON dt.id = vc.data_type_id
    JOIN api_interface ai ON ai.id = vc.interface_id
    WHERE vc.status = 'active'
      AND COALESCE(vc.deleted, FALSE) = FALSE
      AND COALESCE(vc.runtime_mode, 'LEGACY') = 'LEGACY'
      AND vi.vendor_code = 'uapi'
      AND vi.status = 'active'
      AND COALESCE(vi.deleted, FALSE) = FALSE
      AND (
          (ai.interface_code = 'PROGRAMMER_HISTORY_TODAY'
              AND dt.data_type_code = 'programmer_history'
              AND vc.api_url = 'https://uapis.cn/api/v1/history/programmer/today')
          OR
          (ai.interface_code = 'PROGRAMMER_HISTORY_BY_DATE'
              AND dt.data_type_code = 'programmer_history_by_date'
              AND vc.api_url = 'https://uapis.cn/api/v1/history/programmer')
      )
      AND UPPER(vc.method) = 'GET'
      AND vc.timeout = 10000
      AND vc.retry_count = 1
      AND UPPER(COALESCE(vc.auth_type, 'NONE')) = 'NONE'
      AND COALESCE(vc.header_config, '{}'::JSONB) = '{}'::JSONB
      AND COALESCE(vc.request_template, '{}'::JSONB) = '{}'::JSONB
      AND COALESCE(vc.response_mapping, '{}'::JSONB) = '{}'::JSONB
      AND COALESCE(vc.param_mapping, '[]'::JSONB) = '[]'::JSONB
      AND btrim(COALESCE(vc.auth_config, '')) IN ('', '{}')
      AND vc.fallback_vendor_id IS NULL
      AND COALESCE(vc.security_version, 0) = 0
      AND NOT EXISTS (
          SELECT 1 FROM vendor_interface_security_step step
          WHERE step.vendor_config_id = vc.id
      )
      AND NOT EXISTS (
          SELECT 1 FROM vendor_params_mapping mapping
          WHERE mapping.vendor_config_id = vc.id
      )
      AND NOT EXISTS (
          SELECT 1 FROM vendor_connector_version version
          WHERE version.vendor_config_id = vc.id
      )
), seed_pipeline AS (
    SELECT source.*,
           jsonb_build_array(
               jsonb_build_object(
                   'stageKey', 'legacy-request-builder',
                   'capability', 'REQUEST_BUILDER',
                   'pluginId', 'legacy-http',
                   'pluginVersion', '1.0.0',
                   'order', 0,
                   'enabled', TRUE,
                   'config', builder_config,
                   'configHash', v044_connector_sha256(builder_config)
               ),
               jsonb_build_object(
                   'stageKey', 'legacy-request-processor',
                   'capability', 'REQUEST_PROCESSOR',
                   'pluginId', 'legacy-http',
                   'pluginVersion', '1.0.0',
                   'order', 100,
                   'enabled', TRUE,
                   'config', request_processor_config,
                   'configHash', v044_connector_sha256(request_processor_config)
               ),
               jsonb_build_object(
                   'stageKey', 'legacy-transport',
                   'capability', 'TRANSPORT',
                   'pluginId', 'legacy-http',
                   'pluginVersion', '1.0.0',
                   'order', 200,
                   'enabled', TRUE,
                   'config', '{}'::JSONB,
                   'configHash', v044_connector_sha256('{}'::JSONB)
               ),
               jsonb_build_object(
                   'stageKey', 'legacy-response-processor',
                   'capability', 'RESPONSE_PROCESSOR',
                   'pluginId', 'legacy-http',
                   'pluginVersion', '1.0.0',
                   'order', 300,
                   'enabled', TRUE,
                   'config', response_processor_config,
                   'configHash', v044_connector_sha256(response_processor_config)
               ),
               jsonb_build_object(
                   'stageKey', 'legacy-response-parser',
                   'capability', 'RESPONSE_PARSER',
                   'pluginId', 'legacy-http',
                   'pluginVersion', '1.0.0',
                   'order', 400,
                   'enabled', TRUE,
                   'config', '{}'::JSONB,
                   'configHash', v044_connector_sha256('{}'::JSONB)
               ),
               jsonb_build_object(
                   'stageKey', 'legacy-response-normalizer',
                   'capability', 'RESPONSE_NORMALIZER',
                   'pluginId', 'legacy-http',
                   'pluginVersion', '1.0.0',
                   'order', 500,
                   'enabled', TRUE,
                   'config', normalizer_config,
                   'configHash', v044_connector_sha256(normalizer_config)
               )
           ) AS pipeline_snapshot
    FROM seed_source source
), inserted_seed_versions AS (
    INSERT INTO vendor_connector_version (
        vendor_config_id, version_no, draft_version, pipeline_snapshot, snapshot_hash,
        security_version, status, previous_version_id, published_at,
        published_by, created_by, created_at, updated_by, updated_at
    )
    SELECT vendor_config_id, 1, 0, pipeline_snapshot,
           v044_connector_sha256(pipeline_snapshot), security_version,
           'ACTIVE', NULL, CURRENT_TIMESTAMP,
           NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP
    FROM seed_pipeline
    RETURNING id, vendor_config_id
)
UPDATE vendor_config config
SET runtime_mode = 'PLUGIN',
    active_connector_version_id = version.id,
    connector_version = COALESCE(config.connector_version, 0) + 1,
    updated_at = CURRENT_TIMESTAMP
FROM inserted_seed_versions version
WHERE config.id = version.vendor_config_id;

DO $$
DECLARE
    invalid_ids TEXT;
BEGIN
    SELECT string_agg(config.id::TEXT, ',' ORDER BY config.id)
    INTO invalid_ids
    FROM vendor_config config
    LEFT JOIN vendor_connector_version active_version
      ON active_version.id = config.active_connector_version_id
     AND active_version.vendor_config_id = config.id
     AND active_version.status = 'ACTIVE'
    LEFT JOIN vendor_connector_migration migration
      ON migration.vendor_config_id = config.id
    LEFT JOIN api_interface api
      ON api.id = config.interface_id
    LEFT JOIN vendor_info vendor
      ON vendor.id = config.vendor_id
    LEFT JOIN data_type data_type
      ON data_type.id = config.data_type_id
    WHERE config.status = 'active'
      AND COALESCE(config.deleted, FALSE) = FALSE
      AND (
          config.runtime_mode <> 'PLUGIN'
          OR active_version.id IS NULL
          OR NOT (
              (
                  vendor.vendor_code = 'uapi'
                  AND api.interface_code = 'PROGRAMMER_HISTORY_TODAY'
                  AND data_type.data_type_code = 'programmer_history'
                  AND config.api_url = 'https://uapis.cn/api/v1/history/programmer/today'
                  AND active_version.snapshot_hash =
                      '16c8fd2464ad936bcaeb2f1a12e34a3ba3f0a672f228dfae89640026143e64b9'
              )
              OR (
                  vendor.vendor_code = 'uapi'
                  AND api.interface_code = 'PROGRAMMER_HISTORY_BY_DATE'
                  AND data_type.data_type_code = 'programmer_history_by_date'
                  AND config.api_url = 'https://uapis.cn/api/v1/history/programmer'
                  AND active_version.snapshot_hash =
                      'f6c0b4ee7895a90b2b8d10e89b5014c43e5146243025892394840644d757dbea'
              )
              OR (
                  migration.state = 'STABLE'
                  AND migration.observation_gate_passed = TRUE
                  AND migration.published_connector_version_id = active_version.id
                  AND migration.published_version_no = active_version.version_no
              )
          )
      );

    IF invalid_ids IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'V044 blocked: active vendor configurations are not safely migrated: ' || invalid_ids,
            ERRCODE = 'check_violation';
    END IF;
END;
$$;

-- Inactive legacy rows are not executable. Convert only their runtime marker so the database can
-- enforce the one-way PLUGIN invariant; they still need a published connector before reactivation.
UPDATE vendor_config
SET runtime_mode = 'PLUGIN',
    active_connector_version_id = NULL,
    connector_version = COALESCE(connector_version, 0) + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE (status <> 'active' OR COALESCE(deleted, FALSE) = TRUE)
  AND COALESCE(runtime_mode, 'LEGACY') = 'LEGACY';

ALTER TABLE vendor_config
    ALTER COLUMN runtime_mode SET DEFAULT 'PLUGIN';

ALTER TABLE vendor_config
    DROP CONSTRAINT IF EXISTS ck_vendor_config_runtime_mode;

ALTER TABLE vendor_config
    ADD CONSTRAINT ck_vendor_config_runtime_mode
        CHECK (runtime_mode = 'PLUGIN'),
    DROP CONSTRAINT IF EXISTS ck_vendor_config_active_connector;

ALTER TABLE vendor_config
    ADD CONSTRAINT ck_vendor_config_active_connector
        CHECK (COALESCE(deleted, FALSE) = TRUE
            OR status <> 'active'
            OR active_connector_version_id IS NOT NULL);

CREATE OR REPLACE FUNCTION enforce_active_vendor_connector_pointer()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.runtime_mode <> 'PLUGIN' THEN
        RAISE EXCEPTION 'vendor_config % must use PLUGIN runtime', NEW.id;
    END IF;
    IF NEW.status = 'active' AND COALESCE(NEW.deleted, FALSE) = FALSE AND NOT EXISTS (
        SELECT 1
        FROM vendor_connector_version version
        WHERE version.id = NEW.active_connector_version_id
          AND version.vendor_config_id = NEW.id
          AND version.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'active vendor_config % has no matching ACTIVE connector version', NEW.id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_vendor_config_active_connector_pointer ON vendor_config;
CREATE CONSTRAINT TRIGGER trg_vendor_config_active_connector_pointer
    AFTER INSERT OR UPDATE OF runtime_mode, status, deleted, active_connector_version_id
    ON vendor_config
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION enforce_active_vendor_connector_pointer();

CREATE OR REPLACE FUNCTION enforce_referenced_vendor_connector_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    invalid_config_id BIGINT;
BEGIN
    SELECT config.id
    INTO invalid_config_id
    FROM vendor_config config
    LEFT JOIN vendor_connector_version version
      ON version.id = config.active_connector_version_id
     AND version.vendor_config_id = config.id
     AND version.status = 'ACTIVE'
    WHERE config.status = 'active'
      AND COALESCE(config.deleted, FALSE) = FALSE
      AND version.id IS NULL
    ORDER BY config.id
    LIMIT 1;

    IF invalid_config_id IS NOT NULL THEN
        RAISE EXCEPTION 'active vendor_config % references an invalid connector version', invalid_config_id;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_referenced_vendor_connector_version ON vendor_connector_version;
CREATE CONSTRAINT TRIGGER trg_referenced_vendor_connector_version
    AFTER UPDATE OF status, vendor_config_id OR DELETE
    ON vendor_connector_version
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION enforce_referenced_vendor_connector_version();

COMMENT ON COLUMN vendor_config.runtime_mode IS
    'Plugin-only runtime marker. LEGACY execution was retired by V044.';

DROP FUNCTION v044_connector_sha256(JSONB);
DROP FUNCTION v044_connector_canonical_jsonb(JSONB);
