-- External request connector plugin control plane and Access-owned runtime facts.
-- This migration is additive so existing LEGACY vendor configurations continue to run unchanged.

CREATE TABLE IF NOT EXISTS connector_plugin (
    id BIGSERIAL PRIMARY KEY,
    plugin_id VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_connector_plugin_status
    ON connector_plugin(status) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS connector_plugin_version (
    id BIGSERIAL PRIMARY KEY,
    plugin_id VARCHAR(128) NOT NULL REFERENCES connector_plugin(plugin_id),
    version VARCHAR(64) NOT NULL,
    spi_version VARCHAR(32) NOT NULL,
    entry_class VARCHAR(500) NOT NULL,
    artifact_uri VARCHAR(2000) NOT NULL,
    artifact_sha256 CHAR(64) NOT NULL,
    detached_signature TEXT NOT NULL,
    signing_key_id VARCHAR(128) NOT NULL,
    manifest_json JSONB NOT NULL,
    config_schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    permission_manifest JSONB NOT NULL DEFAULT '{}'::jsonb,
    min_host_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'VERIFIED'
        CHECK (status IN ('IMPORTED', 'VERIFIED', 'STAGING', 'STAGING_FAILED', 'ACTIVE', 'DISABLED')),
    safe_error_code VARCHAR(64),
    safe_error_digest VARCHAR(512),
    verified_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plugin_id, version),
    CHECK (artifact_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_connector_plugin_version_status
    ON connector_plugin_version(status);

-- Built-in compatibility plugin: registered directly by Access and never downloaded as an external JAR.
-- Its identity and capabilities must remain aligned with LegacyHttpConnectorPlugin.descriptor().
INSERT INTO connector_plugin (
    plugin_id, display_name, provider, description, status, created_at, updated_at, deleted
)
VALUES (
    'legacy-http', 'Legacy HTTP', 'internal',
    '内置兼容连接器，承接现有HTTP厂商配置；由Access显式注册，不通过外部制品加载',
    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
)
ON CONFLICT (plugin_id) DO UPDATE
SET display_name = EXCLUDED.display_name,
    provider = EXCLUDED.provider,
    description = EXCLUDED.description,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

INSERT INTO connector_plugin_version (
    plugin_id, version, spi_version, entry_class,
    artifact_uri, artifact_sha256, detached_signature, signing_key_id,
    manifest_json, config_schema_json, capabilities, permission_manifest,
    min_host_version, status, verified_at, created_at, updated_at
)
VALUES (
    'legacy-http', '1.0.0', '1.0',
    'com.dataplatform.common.plugin.legacy.LegacyHttpConnectorPlugin',
    'builtin://legacy-http/1.0.0',
    'cc14cbc264ed84857ff56dc61ab0e4ceca128cf3c4bb35042814ef907211af90',
    'builtin', 'builtin',
    $json${
      "manifestVersion":"1",
      "pluginId":"legacy-http",
      "version":"1.0.0",
      "spiVersion":"1.0",
      "displayName":"Legacy HTTP",
      "provider":"internal",
      "description":"Built-in compatibility connector for existing HTTP vendor configurations",
      "entryClass":"com.dataplatform.common.plugin.legacy.LegacyHttpConnectorPlugin",
      "capabilities":[
        "REQUEST_BUILDER","REQUEST_PROCESSOR","TRANSPORT",
        "RESPONSE_PROCESSOR","RESPONSE_PARSER","RESPONSE_NORMALIZER"
      ],
      "minHostVersion":"1.0.0",
      "configSchema":{
        "$schema":"https://json-schema.org/draft/2020-12/schema",
        "type":"object",
        "additionalProperties":true,
        "properties":{
          "apiUrl":{"type":"string","format":"uri"},
          "method":{"type":"string","enum":["GET","POST","PUT","PATCH","DELETE","HEAD"]},
          "requestMapping":{},
          "headers":{"type":"object"},
          "contentType":{"type":"string"},
          "connectTimeoutMs":{"type":"integer","minimum":1},
          "readTimeoutMs":{"type":"integer","minimum":1},
          "totalTimeoutMs":{"type":"integer","minimum":1},
          "idempotencyPolicy":{"type":"string","enum":["IDEMPOTENT","IDEMPOTENT_WITH_KEY","NON_IDEMPOTENT"]},
          "idempotencyKey":{"type":"string"},
          "maxResponseBytes":{"type":"integer","minimum":1},
          "authType":{"type":"string","enum":["NONE","BEARER","BASIC","API_KEY"]},
          "authConfig":{"type":"object"},
          "secretRefs":{"type":"object"},
          "legacySecretAlias":{"type":"string"},
          "securitySteps":{"type":"array","items":{"type":"object"}},
          "responseMapping":{}
        }
      },
      "permissions":{"networkProtocols":["https"],"networkHosts":[]}
    }$json$::jsonb,
    $json${
      "$schema":"https://json-schema.org/draft/2020-12/schema",
      "type":"object",
      "additionalProperties":true,
      "properties":{
        "apiUrl":{"type":"string","format":"uri"},
        "method":{"type":"string","enum":["GET","POST","PUT","PATCH","DELETE","HEAD"]},
        "requestMapping":{},
        "headers":{"type":"object"},
        "contentType":{"type":"string"},
        "connectTimeoutMs":{"type":"integer","minimum":1},
        "readTimeoutMs":{"type":"integer","minimum":1},
        "totalTimeoutMs":{"type":"integer","minimum":1},
        "idempotencyPolicy":{"type":"string","enum":["IDEMPOTENT","IDEMPOTENT_WITH_KEY","NON_IDEMPOTENT"]},
        "idempotencyKey":{"type":"string"},
        "maxResponseBytes":{"type":"integer","minimum":1},
        "authType":{"type":"string","enum":["NONE","BEARER","BASIC","API_KEY"]},
        "authConfig":{"type":"object"},
        "secretRefs":{"type":"object"},
        "legacySecretAlias":{"type":"string"},
        "securitySteps":{"type":"array","items":{"type":"object"}},
        "responseMapping":{}
      }
    }$json$::jsonb,
    '["REQUEST_BUILDER","REQUEST_PROCESSOR","TRANSPORT","RESPONSE_PROCESSOR","RESPONSE_PARSER","RESPONSE_NORMALIZER"]'::jsonb,
    '{"networkProtocols":["https"],"networkHosts":[]}'::jsonb,
    '1.0.0', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (plugin_id, version) DO UPDATE
SET spi_version = EXCLUDED.spi_version,
    entry_class = EXCLUDED.entry_class,
    artifact_uri = EXCLUDED.artifact_uri,
    artifact_sha256 = EXCLUDED.artifact_sha256,
    detached_signature = EXCLUDED.detached_signature,
    signing_key_id = EXCLUDED.signing_key_id,
    manifest_json = EXCLUDED.manifest_json,
    config_schema_json = EXCLUDED.config_schema_json,
    capabilities = EXCLUDED.capabilities,
    permission_manifest = EXCLUDED.permission_manifest,
    min_host_version = EXCLUDED.min_host_version,
    status = 'ACTIVE',
    safe_error_code = NULL,
    safe_error_digest = NULL,
    verified_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS vendor_connector_version (
    id BIGSERIAL PRIMARY KEY,
    vendor_config_id BIGINT NOT NULL REFERENCES vendor_config(id) ON DELETE RESTRICT,
    version_no INTEGER,
    draft_version INTEGER NOT NULL DEFAULT 0,
    pipeline_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    snapshot_hash CHAR(64),
    security_version INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ACTIVE', 'SUPERSEDED')),
    previous_version_id BIGINT REFERENCES vendor_connector_version(id) ON DELETE RESTRICT,
    published_at TIMESTAMP,
    published_by BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status = 'DRAFT' AND version_no IS NULL AND snapshot_hash IS NULL)
        OR (status <> 'DRAFT' AND version_no IS NOT NULL AND snapshot_hash ~ '^[0-9a-f]{64}$'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_vendor_connector_draft
    ON vendor_connector_version(vendor_config_id) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX IF NOT EXISTS uk_vendor_connector_version_no
    ON vendor_connector_version(vendor_config_id, version_no) WHERE version_no IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_vendor_connector_status
    ON vendor_connector_version(vendor_config_id, status);

CREATE TABLE IF NOT EXISTS vendor_connector_test_fact (
    id BIGSERIAL PRIMARY KEY,
    vendor_config_id BIGINT NOT NULL REFERENCES vendor_config(id) ON DELETE RESTRICT,
    draft_version INTEGER NOT NULL CHECK (draft_version > 0),
    snapshot_hash CHAR(64) NOT NULL CHECK (snapshot_hash ~ '^[0-9a-f]{64}$'),
    plugin_bindings JSONB NOT NULL DEFAULT '[]'::jsonb,
    test_succeeded BOOLEAN NOT NULL,
    safe_error_category VARCHAR(64),
    safe_error_code VARCHAR(128),
    result_digest CHAR(64) NOT NULL CHECK (result_digest ~ '^[0-9a-f]{64}$'),
    tested_by BIGINT,
    tested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vendor_connector_test_fact_gate
    ON vendor_connector_test_fact(vendor_config_id, draft_version, snapshot_hash)
    WHERE test_succeeded = TRUE;
CREATE INDEX IF NOT EXISTS idx_vendor_connector_test_fact_plugins
    ON vendor_connector_test_fact USING GIN(plugin_bindings);

CREATE OR REPLACE FUNCTION reject_vendor_connector_test_fact_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'vendor_connector_test_fact is immutable';
END;
$$;

DROP TRIGGER IF EXISTS trg_vendor_connector_test_fact_immutable ON vendor_connector_test_fact;
CREATE TRIGGER trg_vendor_connector_test_fact_immutable
    BEFORE UPDATE OR DELETE ON vendor_connector_test_fact
    FOR EACH ROW EXECUTE FUNCTION reject_vendor_connector_test_fact_mutation();

ALTER TABLE vendor_config
    ADD COLUMN IF NOT EXISTS runtime_mode VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS active_connector_version_id BIGINT,
    ADD COLUMN IF NOT EXISTS connector_version INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.vendor_config'::regclass
          AND conname = 'ck_vendor_config_runtime_mode'
    ) THEN
        ALTER TABLE vendor_config
            ADD CONSTRAINT ck_vendor_config_runtime_mode
            CHECK (runtime_mode IN ('LEGACY', 'PLUGIN'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.vendor_config'::regclass
          AND conname = 'fk_vendor_config_active_connector_version'
    ) THEN
        ALTER TABLE vendor_config
            ADD CONSTRAINT fk_vendor_config_active_connector_version
            FOREIGN KEY (active_connector_version_id)
            REFERENCES vendor_connector_version(id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_vendor_config_active_connector
    ON vendor_config(active_connector_version_id) WHERE active_connector_version_id IS NOT NULL;

-- Access owns this table and its application-level Mapper/Service. It is created here only because
-- the repository uses one authoritative Liquibase changelog for all five domains.
CREATE TABLE IF NOT EXISTS connector_plugin_activation (
    id BIGSERIAL PRIMARY KEY,
    service_instance_id VARCHAR(200) NOT NULL,
    plugin_id VARCHAR(128) NOT NULL,
    plugin_version VARCHAR(64) NOT NULL,
    artifact_sha256 CHAR(64) NOT NULL,
    host_version VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL
        CHECK (state IN ('LOADING', 'READY', 'FAILED', 'RELEASING', 'RELEASED')),
    loaded_at TIMESTAMP,
    last_heartbeat_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    safe_error_code VARCHAR(64),
    safe_error_digest VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(service_instance_id, plugin_id, plugin_version),
    CHECK (artifact_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_connector_plugin_activation_lookup
    ON connector_plugin_activation(plugin_id, plugin_version, state);
CREATE INDEX IF NOT EXISTS idx_connector_plugin_activation_heartbeat
    ON connector_plugin_activation(last_heartbeat_at);

ALTER TABLE call_record
    ADD COLUMN IF NOT EXISTS plugin_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS plugin_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pipeline_version INTEGER,
    ADD COLUMN IF NOT EXISTS snapshot_hash CHAR(64);

CREATE INDEX IF NOT EXISTS idx_call_record_plugin_trace
    ON call_record(plugin_id, plugin_version, call_time);
CREATE INDEX IF NOT EXISTS idx_call_record_pipeline_trace
    ON call_record(pipeline_version, snapshot_hash, call_time);

INSERT INTO permission (
    permission_code, permission_name, resource_type, resource_path,
    parent_id, sort_order, description, status, deleted
)
VALUES
    ('connector-plugin:view', '连接器插件-查看', 'page', '/connector-plugin', 0, 70, '查看插件目录、版本和激活状态', 'active', FALSE),
    ('connector-plugin:import', '连接器插件-导入', 'button', '/connector-plugin/import', 0, 71, '从受信制品仓库导入签名插件', 'active', FALSE),
    ('connector-plugin:verify', '连接器插件-验证', 'button', '/connector-plugin/verify', 0, 72, '重新验证插件制品和签名', 'active', FALSE),
    ('connector-plugin:activate', '连接器插件-激活', 'button', '/connector-plugin/activate', 0, 73, '预加载并激活插件版本', 'active', FALSE),
    ('connector-plugin:disable', '连接器插件-禁用', 'button', '/connector-plugin/disable', 0, 74, '禁止插件版本用于新的连接器绑定', 'active', FALSE),
    ('connector-plugin:bind', '连接器插件-绑定', 'button', '/vendor/config/connector', 0, 75, '维护厂商连接器草稿', 'active', FALSE),
    ('connector-plugin:test', '连接器插件-测试', 'button', '/vendor/config/connector/test', 0, 76, '执行受控连接器测试', 'active', FALSE),
    ('connector-plugin:publish', '连接器插件-发布', 'button', '/vendor/config/connector/publish', 0, 77, '发布厂商连接器版本', 'active', FALSE),
    ('connector-plugin:rollback', '连接器插件-回滚', 'button', '/vendor/config/connector/rollback', 0, 78, '回滚厂商连接器版本', 'active', FALSE)
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    resource_type = EXCLUDED.resource_type,
    resource_path = EXCLUDED.resource_path,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role_info.id, permission.id, CURRENT_TIMESTAMP
FROM role_info
JOIN permission ON permission.permission_code LIKE 'connector-plugin:%'
WHERE LOWER(role_info.role_code) = 'admin'
  AND role_info.status = 'active'
  AND role_info.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;

COMMENT ON TABLE connector_plugin IS 'Masterdata-owned connector plugin identity catalog';
COMMENT ON TABLE connector_plugin_version IS 'Masterdata-owned immutable signed connector plugin artifacts';
COMMENT ON TABLE vendor_connector_version IS 'Masterdata-owned vendor connector drafts and immutable published snapshots';
COMMENT ON TABLE connector_plugin_activation IS 'Access-owned per-instance connector plugin loading facts';
COMMENT ON COLUMN vendor_config.runtime_mode IS 'LEGACY keeps the original adapter path; PLUGIN uses the active connector snapshot';
COMMENT ON COLUMN vendor_config.active_connector_version_id IS 'Pinned immutable active connector snapshot';
COMMENT ON COLUMN vendor_config.connector_version IS 'Optimistic version for connector mode and active pointer updates';
COMMENT ON COLUMN call_record.plugin_id IS 'Actual connector plugin used for the call';
COMMENT ON COLUMN call_record.plugin_version IS 'Actual connector plugin version used for the call';
COMMENT ON COLUMN call_record.pipeline_version IS 'Actual vendor connector version number used for the call';
COMMENT ON COLUMN call_record.snapshot_hash IS 'SHA-256 of the immutable runtime snapshot used for the call';
