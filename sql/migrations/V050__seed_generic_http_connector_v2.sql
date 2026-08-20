-- Seed the host-owned generic-http:2.0.0 catalogue fact without mutating any
-- existing connector catalogue or runtime history. Liquibase runs this file in
-- one transaction; every failed precondition or drift check therefore leaves
-- the database unchanged.

-- Establish a transaction-local pg_temp namespace for canonical hash checks.
-- These helpers never replace or survive as public database objects.
CREATE TEMPORARY TABLE v050_session_namespace_guard(id INTEGER) ON COMMIT DROP;

CREATE FUNCTION pg_temp.v050_canonical_jsonb(value JSONB)
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
                    to_json(key)::TEXT || ':' || pg_temp.v050_canonical_jsonb(item),
                    ',' ORDER BY key), '') || '}'
            INTO canonical
            FROM jsonb_each(value) AS entry(key, item);
        WHEN 'array' THEN
            SELECT '[' || COALESCE(string_agg(
                    pg_temp.v050_canonical_jsonb(item),
                    ',' ORDER BY ordinal), '') || ']'
            INTO canonical
            FROM jsonb_array_elements(value) WITH ORDINALITY AS entry(item, ordinal);
        ELSE
            canonical := value::TEXT;
    END CASE;
    RETURN canonical;
END;
$$;

CREATE FUNCTION pg_temp.v050_sha256(value JSONB)
RETURNS CHAR(64)
LANGUAGE SQL
IMMUTABLE
STRICT
AS $$
    SELECT encode(sha256(convert_to(pg_temp.v050_canonical_jsonb(value), 'UTF8')), 'hex')::CHAR(64)
$$;

LOCK TABLE connector_plugin, connector_plugin_version IN SHARE ROW EXCLUSIVE MODE;

DO $v050$
DECLARE
    expected_manifest CONSTANT JSONB := $manifest${"authoringModel":"SIMPLE_CONNECTOR","capabilities":["REQUEST_BUILDER","REQUEST_PROCESSOR","RESPONSE_PARSER"],"compatibility":{"dataTypeCodes":["*"],"vendorCodes":["*"]},"configSchema":{"$schema":"https://json-schema.org/draft/2020-12/schema","additionalProperties":false,"properties":{"auth":{"additionalProperties":false,"allOf":[{"if":{"properties":{"type":{"enum":["NONE"]}}},"then":{"not":{"anyOf":[{"required":["tokenRef"]},{"required":["usernameRef"]},{"required":["passwordRef"]},{"required":["keyName"]},{"required":["keyRef"]},{"required":["location"]}]},"required":["type"]}},{"if":{"properties":{"type":{"enum":["BEARER"]}}},"then":{"not":{"anyOf":[{"required":["usernameRef"]},{"required":["passwordRef"]},{"required":["keyName"]},{"required":["keyRef"]},{"required":["location"]}]},"required":["type","tokenRef"]}},{"if":{"properties":{"type":{"enum":["BASIC"]}}},"then":{"not":{"anyOf":[{"required":["tokenRef"]},{"required":["keyName"]},{"required":["keyRef"]},{"required":["location"]}]},"required":["type","usernameRef","passwordRef"]}},{"if":{"properties":{"type":{"enum":["API_KEY"]}}},"then":{"not":{"anyOf":[{"required":["tokenRef"]},{"required":["usernameRef"]},{"required":["passwordRef"]}]},"required":["type","keyName","keyRef","location"]}}],"properties":{"keyName":{"maxLength":128,"minLength":1,"type":"string","x-ui-visible-if":{"type":"API_KEY"}},"keyRef":{"maxLength":256,"minLength":1,"type":"string","x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"API_KEY"}},"location":{"enum":["header","query"],"type":"string","x-ui-visible-if":{"type":"API_KEY"}},"passwordRef":{"maxLength":256,"minLength":1,"type":"string","x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"BASIC"}},"tokenRef":{"maxLength":256,"minLength":1,"type":"string","x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"BEARER"}},"type":{"enum":["NONE","BEARER","BASIC","API_KEY"],"type":"string"},"usernameRef":{"maxLength":256,"minLength":1,"type":"string","x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"BASIC"}}},"required":["type"],"type":"object","x-ui-group":"authentication"},"businessCodePath":{"maxLength":256,"minLength":1,"type":"string","x-ui-advanced":true,"x-ui-group":"response"},"contentType":{"enum":["application/json","application/json; charset=utf-8","application/x-www-form-urlencoded"],"type":"string","x-ui-group":"request"},"dataPath":{"maxLength":256,"minLength":1,"type":"string","x-ui-advanced":true,"x-ui-group":"response"},"endpoint":{"maxLength":2048,"minLength":1,"type":"string","x-ui-group":"request"},"headers":{"items":{"additionalProperties":false,"properties":{"name":{"maxLength":128,"minLength":1,"type":"string"},"value":{"maxLength":4096,"minLength":1,"type":"string"}},"required":["name","value"],"type":"object"},"maxItems":64,"type":"array","x-ui-advanced":true,"x-ui-group":"request"},"method":{"enum":["GET","POST","PUT","PATCH","DELETE","HEAD"],"type":"string","x-ui-group":"request"},"requestMapping":{"items":{"additionalProperties":false,"properties":{"defaultValue":{},"required":{"type":"boolean"},"sourceField":{"maxLength":256,"minLength":1,"type":"string"},"targetField":{"maxLength":256,"minLength":1,"type":"string"},"transformType":{"enum":["none","trim","uppercase","lowercase"],"type":"string"}},"required":["sourceField","targetField"],"type":"object"},"maxItems":256,"type":"array","x-ui-group":"request"},"successBusinessCodes":{"items":{"maxLength":128,"minLength":1,"type":"string"},"maxItems":128,"minItems":1,"type":"array","uniqueItems":true,"x-ui-advanced":true,"x-ui-group":"response"},"successHttpStatuses":{"items":{"maximum":599,"minimum":100,"type":"integer"},"maxItems":100,"minItems":1,"type":"array","uniqueItems":true,"x-ui-group":"response"}},"required":["endpoint","method","auth"],"type":"object","x-platform-managed":["transport","timeouts","retry","responseMapping"]},"connectorKind":"GENERIC_HTTP","description":"Built-in standard single-request HTTPS connector","displayName":"Generic HTTP","entryClass":"com.dataplatform.common.plugin.runtime.GenericHttpConnectorPlugin","manifestVersion":"2","minHostVersion":"1.0.0","outputMode":"HOST_MAPPING","permissions":{"networkHosts":[],"networkProtocols":[]},"pluginId":"generic-http","provider":"data-platform","spiVersion":"1.1","transportMode":"HOST_SINGLE_HTTP","version":"2.0.0"}$manifest$::JSONB;
    expected_schema CONSTANT JSONB := expected_manifest->'configSchema';
    expected_permissions CONSTANT JSONB := expected_manifest->'permissions';
    expected_compatibility CONSTANT JSONB := expected_manifest->'compatibility';
    missing_dependencies TEXT;
BEGIN
    SELECT string_agg(required.name, ',' ORDER BY required.name)
    INTO missing_dependencies
    FROM (VALUES
        ('V049 changeset', EXISTS (
            SELECT 1 FROM databasechangelog
            WHERE id = 'add-connector-product-spec-2026-08-12'
              AND author = 'data-platform')),
        ('V049 plugin projection constraint', EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.connector_plugin_version'::regclass
              AND conname = 'ck_connector_plugin_manifest_projection_v049'
              AND convalidated)),
        ('V049 plugin binding constraint', EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.connector_plugin_version'::regclass
              AND conname = 'ck_connector_plugin_manifest_binding_v049'
              AND convalidated)),
        ('V047 plugin version function',
            to_regprocedure('public.reject_connector_plugin_version_identity_mutation()') IS NOT NULL),
        ('V047 plugin version trigger', EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgrelid = 'public.connector_plugin_version'::regclass
              AND tgname = 'trg_connector_plugin_version_immutable'
              AND tgenabled = 'O' AND NOT tgisinternal)),
        ('V047 plugin parent trigger', EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgrelid = 'public.connector_plugin'::regclass
              AND tgname = 'trg_connector_plugin_reject_delete'
              AND tgenabled = 'O' AND NOT tgisinternal)),
        ('manifest_version column', EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'connector_plugin_version'
              AND column_name = 'manifest_version' AND is_nullable = 'NO')),
        ('authoring_model column', EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'connector_plugin_version'
              AND column_name = 'authoring_model' AND is_nullable = 'NO')),
        ('connector_kind column', EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'connector_plugin_version'
              AND column_name = 'connector_kind')),
        ('transport_mode column', EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'connector_plugin_version'
              AND column_name = 'transport_mode')),
        ('output_mode column', EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'connector_plugin_version'
              AND column_name = 'output_mode')),
        ('compatibility_manifest column', EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'connector_plugin_version'
              AND column_name = 'compatibility_manifest'))
    ) AS required(name, present)
    WHERE NOT required.present;

    IF missing_dependencies IS NOT NULL THEN
        RAISE EXCEPTION 'V050 blocked: V049/V047 catalogue prerequisites are missing: %',
            missing_dependencies;
    END IF;

    IF btrim(pg_temp.v050_sha256(expected_manifest)) IS DISTINCT FROM
            '09c76d80db477049a376e0d845f633d098301e712132f27e926b5a7877f60650'
       OR btrim(pg_temp.v050_sha256(expected_schema)) IS DISTINCT FROM
            '349bbb8804fa868bf3630b717099cd8531fd33a6982b58581e63b19e9cd60f70' THEN
        RAISE EXCEPTION 'V050 blocked: SQL canonical Manifest/Schema diverges from host metadata';
    END IF;

    IF EXISTS (
        SELECT 1 FROM connector_plugin
        WHERE plugin_id = 'generic-http'
          AND (display_name IS DISTINCT FROM 'Generic HTTP'
            OR provider IS DISTINCT FROM 'data-platform'
            OR description IS DISTINCT FROM 'Built-in standard single-request HTTPS connector'
            OR status IS DISTINCT FROM 'ACTIVE'
            OR deleted IS DISTINCT FROM FALSE
            OR created_by IS NOT NULL
            OR updated_by IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'V050 blocked: generic-http parent catalogue fact drifted';
    END IF;

    IF EXISTS (
        SELECT 1 FROM connector_plugin_version
        WHERE plugin_id = 'generic-http' AND version <> '2.0.0'
    ) THEN
        RAISE EXCEPTION 'V050 blocked: unexpected generic-http version already exists';
    END IF;

    IF EXISTS (
        SELECT 1 FROM connector_plugin_version
        WHERE plugin_id = 'generic-http' AND version = '2.0.0'
          AND (spi_version IS DISTINCT FROM '1.1'
            OR entry_class IS DISTINCT FROM 'com.dataplatform.common.plugin.runtime.GenericHttpConnectorPlugin'
            OR artifact_uri IS DISTINCT FROM 'builtin://generic-http/2.0.0'
            OR lower(btrim(artifact_sha256)) IS DISTINCT FROM '8f0f535850e77d2680a3159e2de1044c61024cebe13bc55089f4566ef1744b16'
            OR detached_signature IS DISTINCT FROM 'builtin'
            OR signing_key_id IS DISTINCT FROM 'builtin'
            OR manifest_json IS DISTINCT FROM expected_manifest
            OR config_schema_json IS DISTINCT FROM expected_schema
            OR capabilities IS DISTINCT FROM '["REQUEST_BUILDER","REQUEST_PROCESSOR","RESPONSE_PARSER"]'::JSONB
            OR permission_manifest IS DISTINCT FROM expected_permissions
            OR min_host_version IS DISTINCT FROM '1.0.0'
            OR status IS DISTINCT FROM 'ACTIVE'
            OR safe_error_code IS NOT NULL
            OR safe_error_digest IS NOT NULL
            OR verified_at IS NULL
            OR created_by IS NOT NULL
            OR updated_by IS NOT NULL
            OR manifest_version IS DISTINCT FROM '2'
            OR authoring_model IS DISTINCT FROM 'SIMPLE_CONNECTOR'
            OR connector_kind IS DISTINCT FROM 'GENERIC_HTTP'
            OR transport_mode IS DISTINCT FROM 'HOST_SINGLE_HTTP'
            OR output_mode IS DISTINCT FROM 'HOST_MAPPING'
            OR compatibility_manifest IS DISTINCT FROM expected_compatibility)
    ) THEN
        RAISE EXCEPTION 'V050 blocked: generic-http:2.0.0 catalogue fact drifted';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM connector_plugin WHERE plugin_id = 'generic-http') THEN
        INSERT INTO connector_plugin (
            plugin_id, display_name, provider, description, status,
            created_by, created_at, updated_by, updated_at, deleted
        ) VALUES (
            'generic-http', 'Generic HTTP', 'data-platform',
            'Built-in standard single-request HTTPS connector', 'ACTIVE',
            NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, FALSE
        )
        ON CONFLICT (plugin_id) DO NOTHING;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM connector_plugin_version
        WHERE plugin_id = 'generic-http' AND version = '2.0.0'
    ) THEN
        INSERT INTO connector_plugin_version (
            plugin_id, version, spi_version, entry_class,
            artifact_uri, artifact_sha256, detached_signature, signing_key_id,
            manifest_json, config_schema_json, capabilities, permission_manifest,
            min_host_version, status, safe_error_code, safe_error_digest,
            verified_at, created_by, created_at, updated_by, updated_at,
            manifest_version, authoring_model, connector_kind, transport_mode,
            output_mode, compatibility_manifest
        ) VALUES (
            'generic-http', '2.0.0', '1.1',
            'com.dataplatform.common.plugin.runtime.GenericHttpConnectorPlugin',
            'builtin://generic-http/2.0.0',
            '8f0f535850e77d2680a3159e2de1044c61024cebe13bc55089f4566ef1744b16',
            'builtin', 'builtin', expected_manifest, expected_schema,
            '["REQUEST_BUILDER","REQUEST_PROCESSOR","RESPONSE_PARSER"]'::JSONB,
            expected_permissions, '1.0.0', 'ACTIVE', NULL, NULL,
            CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP,
            '2', 'SIMPLE_CONNECTOR', 'GENERIC_HTTP', 'HOST_SINGLE_HTTP',
            'HOST_MAPPING', expected_compatibility
        )
        ON CONFLICT (plugin_id, version) DO NOTHING;
    END IF;

    IF (SELECT count(*) FROM connector_plugin WHERE plugin_id = 'generic-http') <> 1
       OR EXISTS (
            SELECT 1 FROM connector_plugin
            WHERE plugin_id = 'generic-http'
              AND (display_name IS DISTINCT FROM 'Generic HTTP'
                OR provider IS DISTINCT FROM 'data-platform'
                OR description IS DISTINCT FROM 'Built-in standard single-request HTTPS connector'
                OR status IS DISTINCT FROM 'ACTIVE'
                OR deleted IS DISTINCT FROM FALSE
                OR created_by IS NOT NULL OR updated_by IS NOT NULL)) THEN
        RAISE EXCEPTION 'V050 blocked: generic-http parent postcondition failed';
    END IF;

    IF (SELECT count(*) FROM connector_plugin_version WHERE plugin_id = 'generic-http') <> 1
       OR NOT EXISTS (
            SELECT 1 FROM connector_plugin_version
            WHERE plugin_id = 'generic-http' AND version = '2.0.0'
              AND spi_version = '1.1'
              AND entry_class = 'com.dataplatform.common.plugin.runtime.GenericHttpConnectorPlugin'
              AND artifact_uri = 'builtin://generic-http/2.0.0'
              AND lower(btrim(artifact_sha256)) = '8f0f535850e77d2680a3159e2de1044c61024cebe13bc55089f4566ef1744b16'
              AND detached_signature = 'builtin' AND signing_key_id = 'builtin'
              AND manifest_json = expected_manifest
              AND config_schema_json = expected_schema
              AND capabilities = '["REQUEST_BUILDER","REQUEST_PROCESSOR","RESPONSE_PARSER"]'::JSONB
              AND permission_manifest = expected_permissions
              AND min_host_version = '1.0.0' AND status = 'ACTIVE'
              AND safe_error_code IS NULL AND safe_error_digest IS NULL
              AND verified_at IS NOT NULL AND created_by IS NULL AND updated_by IS NULL
              AND manifest_version = '2' AND authoring_model = 'SIMPLE_CONNECTOR'
              AND connector_kind = 'GENERIC_HTTP'
              AND transport_mode = 'HOST_SINGLE_HTTP' AND output_mode = 'HOST_MAPPING'
              AND compatibility_manifest = expected_compatibility) THEN
        RAISE EXCEPTION 'V050 blocked: generic-http:2.0.0 postcondition failed';
    END IF;
END;
$v050$;

DROP FUNCTION pg_temp.v050_sha256(JSONB);
DROP FUNCTION pg_temp.v050_canonical_jsonb(JSONB);
