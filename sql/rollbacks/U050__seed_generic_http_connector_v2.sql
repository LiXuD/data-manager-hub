-- Remove only the exact generic-http:2.0.0 seed and only while no control-plane,
-- runtime, call, or billing fact refers to generic-http. Liquibase executes the
-- rollback in one transaction, so every HALT restores both data and triggers.

LOCK TABLE connector_plugin, connector_plugin_version,
    vendor_connector_version, vendor_connector_test_fact,
    connector_plugin_activation, call_record, billing_event
    IN SHARE ROW EXCLUSIVE MODE;

DO $u050$
DECLARE
    expected_manifest CONSTANT JSONB := $manifest${"authoringModel":"SIMPLE_CONNECTOR","capabilities":["REQUEST_BUILDER","REQUEST_PROCESSOR","RESPONSE_PARSER"],"compatibility":{"dataTypeCodes":["*"],"vendorCodes":["*"]},"configSchema":{"$schema":"https://json-schema.org/draft/2020-12/schema","additionalProperties":false,"properties":{"auth":{"additionalProperties":false,"allOf":[{"if":{"properties":{"type":{"enum":["NONE"]}}},"then":{"not":{"anyOf":[{"required":["tokenRef"]},{"required":["usernameRef"]},{"required":["passwordRef"]},{"required":["keyName"]},{"required":["keyRef"]},{"required":["location"]}]},"required":["type"]}},{"if":{"properties":{"type":{"enum":["BEARER"]}}},"then":{"not":{"anyOf":[{"required":["usernameRef"]},{"required":["passwordRef"]},{"required":["keyName"]},{"required":["keyRef"]},{"required":["location"]}]},"required":["type","tokenRef"]}},{"if":{"properties":{"type":{"enum":["BASIC"]}}},"then":{"not":{"anyOf":[{"required":["tokenRef"]},{"required":["keyName"]},{"required":["keyRef"]},{"required":["location"]}]},"required":["type","usernameRef","passwordRef"]}},{"if":{"properties":{"type":{"enum":["API_KEY"]}}},"then":{"not":{"anyOf":[{"required":["tokenRef"]},{"required":["usernameRef"]},{"required":["passwordRef"]}]},"required":["type","keyName","keyRef","location"]}}],"properties":{"keyName":{"maxLength":128,"minLength":1,"type":"string","x-ui-visible-if":{"type":"API_KEY"}},"keyRef":{"maxLength":256,"minLength":1,"type":"string","x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"API_KEY"}},"location":{"enum":["header","query"],"type":"string","x-ui-visible-if":{"type":"API_KEY"}},"passwordRef":{"maxLength":256,"minLength":1,"type":"string","x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"BASIC"}},"tokenRef":{"maxLength":256,"minLength":1,"type":"string","x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"BEARER"}},"type":{"enum":["NONE","BEARER","BASIC","API_KEY"],"type":"string"},"usernameRef":{"maxLength":256,"minLength":1,"type":"string","x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"BASIC"}}},"required":["type"],"type":"object","x-ui-group":"authentication"},"businessCodePath":{"maxLength":256,"minLength":1,"type":"string","x-ui-advanced":true,"x-ui-group":"response"},"contentType":{"enum":["application/json","application/json; charset=utf-8","application/x-www-form-urlencoded"],"type":"string","x-ui-group":"request"},"dataPath":{"maxLength":256,"minLength":1,"type":"string","x-ui-advanced":true,"x-ui-group":"response"},"endpoint":{"maxLength":2048,"minLength":1,"type":"string","x-ui-group":"request"},"headers":{"items":{"additionalProperties":false,"properties":{"name":{"maxLength":128,"minLength":1,"type":"string"},"value":{"maxLength":4096,"minLength":1,"type":"string"}},"required":["name","value"],"type":"object"},"maxItems":64,"type":"array","x-ui-advanced":true,"x-ui-group":"request"},"method":{"enum":["GET","POST","PUT","PATCH","DELETE","HEAD"],"type":"string","x-ui-group":"request"},"requestMapping":{"items":{"additionalProperties":false,"properties":{"defaultValue":{},"required":{"type":"boolean"},"sourceField":{"maxLength":256,"minLength":1,"type":"string"},"targetField":{"maxLength":256,"minLength":1,"type":"string"},"transformType":{"enum":["none","trim","uppercase","lowercase"],"type":"string"}},"required":["sourceField","targetField"],"type":"object"},"maxItems":256,"type":"array","x-ui-group":"request"},"successBusinessCodes":{"items":{"maxLength":128,"minLength":1,"type":"string"},"maxItems":128,"minItems":1,"type":"array","uniqueItems":true,"x-ui-advanced":true,"x-ui-group":"response"},"successHttpStatuses":{"items":{"maximum":599,"minimum":100,"type":"integer"},"maxItems":100,"minItems":1,"type":"array","uniqueItems":true,"x-ui-group":"response"}},"required":["endpoint","method","auth"],"type":"object","x-platform-managed":["transport","timeouts","retry","responseMapping"]},"connectorKind":"GENERIC_HTTP","description":"Built-in standard single-request HTTPS connector","displayName":"Generic HTTP","entryClass":"com.dataplatform.common.plugin.runtime.GenericHttpConnectorPlugin","manifestVersion":"2","minHostVersion":"1.0.0","outputMode":"HOST_MAPPING","permissions":{"networkHosts":[],"networkProtocols":[]},"pluginId":"generic-http","provider":"data-platform","spiVersion":"1.1","transportMode":"HOST_SINGLE_HTTP","version":"2.0.0"}$manifest$::JSONB;
    expected_schema CONSTANT JSONB := expected_manifest->'configSchema';
    parent_count INTEGER;
    exact_version_count INTEGER;
    all_version_count INTEGER;
    deleted_count INTEGER;
    blocking_references TEXT;
BEGIN
    SELECT count(*) INTO parent_count
    FROM connector_plugin WHERE plugin_id = 'generic-http';
    SELECT count(*) INTO exact_version_count
    FROM connector_plugin_version
    WHERE plugin_id = 'generic-http' AND version = '2.0.0';
    SELECT count(*) INTO all_version_count
    FROM connector_plugin_version WHERE plugin_id = 'generic-http';

    SELECT string_agg(blocker.kind || ':' || blocker.count::TEXT, ',' ORDER BY blocker.kind)
    INTO blocking_references
    FROM (
        SELECT 'activation' AS kind, count(*) AS count
        FROM connector_plugin_activation WHERE plugin_id = 'generic-http'
        HAVING count(*) > 0
        UNION ALL
        SELECT 'billing', count(*) FROM billing_event
        WHERE plugin_id = 'generic-http' HAVING count(*) > 0
        UNION ALL
        SELECT 'call', count(*) FROM call_record
        WHERE plugin_id = 'generic-http' HAVING count(*) > 0
        UNION ALL
        SELECT 'connector', count(*)
        FROM vendor_connector_version version
        WHERE (jsonb_typeof(version.connector_spec) = 'object'
               AND version.connector_spec #>> '{plugin,pluginId}' = 'generic-http')
           OR (jsonb_typeof(version.pipeline_snapshot) = 'array' AND EXISTS (
                SELECT 1 FROM jsonb_array_elements(version.pipeline_snapshot) AS step(item)
                WHERE step.item->>'pluginId' = 'generic-http'))
        HAVING count(*) > 0
        UNION ALL
        SELECT 'test', count(*)
        FROM vendor_connector_test_fact fact
        WHERE jsonb_typeof(fact.plugin_bindings) = 'array'
          AND EXISTS (
              SELECT 1 FROM jsonb_array_elements_text(fact.plugin_bindings) AS binding(value)
              WHERE binding.value LIKE 'generic-http:%')
        HAVING count(*) > 0
    ) blocker;

    IF blocking_references IS NOT NULL THEN
        RAISE EXCEPTION 'U050 rollback HALT: generic-http is referenced: %', blocking_references;
    END IF;

    IF parent_count = 0 AND all_version_count = 0 THEN
        RETURN;
    END IF;

    IF parent_count <> 1 OR exact_version_count <> 1 OR all_version_count <> 1 THEN
        RAISE EXCEPTION
            'U050 rollback HALT: generic-http catalogue is partial or has unexpected versions (parent %, exact %, all %)',
            parent_count, exact_version_count, all_version_count;
    END IF;

    IF EXISTS (
        SELECT 1 FROM connector_plugin
        WHERE plugin_id = 'generic-http'
          AND (display_name IS DISTINCT FROM 'Generic HTTP'
            OR provider IS DISTINCT FROM 'data-platform'
            OR description IS DISTINCT FROM 'Built-in standard single-request HTTPS connector'
            OR status IS DISTINCT FROM 'ACTIVE'
            OR deleted IS DISTINCT FROM FALSE
            OR created_by IS NOT NULL OR updated_by IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'U050 rollback HALT: generic-http parent catalogue fact drifted';
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
            OR permission_manifest IS DISTINCT FROM '{"networkHosts":[],"networkProtocols":[]}'::JSONB
            OR min_host_version IS DISTINCT FROM '1.0.0'
            OR status IS DISTINCT FROM 'ACTIVE'
            OR safe_error_code IS NOT NULL OR safe_error_digest IS NOT NULL
            OR verified_at IS NULL OR created_by IS NOT NULL OR updated_by IS NOT NULL
            OR manifest_version IS DISTINCT FROM '2'
            OR authoring_model IS DISTINCT FROM 'SIMPLE_CONNECTOR'
            OR connector_kind IS DISTINCT FROM 'GENERIC_HTTP'
            OR transport_mode IS DISTINCT FROM 'HOST_SINGLE_HTTP'
            OR output_mode IS DISTINCT FROM 'HOST_MAPPING'
            OR compatibility_manifest IS DISTINCT FROM
                '{"dataTypeCodes":["*"],"vendorCodes":["*"]}'::JSONB)
    ) THEN
        RAISE EXCEPTION 'U050 rollback HALT: generic-http:2.0.0 catalogue fact drifted';
    END IF;

    ALTER TABLE connector_plugin_version
        DISABLE TRIGGER trg_connector_plugin_version_immutable;
    DELETE FROM connector_plugin_version
    WHERE plugin_id = 'generic-http' AND version = '2.0.0';
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    ALTER TABLE connector_plugin_version
        ENABLE TRIGGER trg_connector_plugin_version_immutable;
    IF deleted_count <> 1 THEN
        RAISE EXCEPTION 'U050 rollback HALT: expected one generic-http version delete, got %',
            deleted_count;
    END IF;

    -- V047 also protects parent rows from physical deletion. Disable only its
    -- exact trigger for this seed row and restore it immediately.
    ALTER TABLE connector_plugin DISABLE TRIGGER trg_connector_plugin_reject_delete;
    DELETE FROM connector_plugin WHERE plugin_id = 'generic-http';
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    ALTER TABLE connector_plugin ENABLE TRIGGER trg_connector_plugin_reject_delete;
    IF deleted_count <> 1 THEN
        RAISE EXCEPTION 'U050 rollback HALT: expected one generic-http parent delete, got %',
            deleted_count;
    END IF;

    IF EXISTS (SELECT 1 FROM connector_plugin WHERE plugin_id = 'generic-http')
       OR EXISTS (SELECT 1 FROM connector_plugin_version WHERE plugin_id = 'generic-http')
       OR NOT EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgrelid = 'public.connector_plugin_version'::regclass
              AND tgname = 'trg_connector_plugin_version_immutable'
              AND tgenabled = 'O' AND NOT tgisinternal)
       OR NOT EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgrelid = 'public.connector_plugin'::regclass
              AND tgname = 'trg_connector_plugin_reject_delete'
              AND tgenabled = 'O' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'U050 rollback HALT: delete or trigger restoration postcondition failed';
    END IF;
END;
$u050$;
