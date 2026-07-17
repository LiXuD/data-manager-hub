ALTER TABLE vendor_config
    ADD COLUMN IF NOT EXISTS security_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS vendor_interface_security_step (
    id BIGSERIAL PRIMARY KEY,
    vendor_config_id BIGINT NOT NULL,
    step_key VARCHAR(100) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    step_name VARCHAR(100),
    sort_no INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vendor_security_step_config
        FOREIGN KEY (vendor_config_id) REFERENCES vendor_config(id) ON DELETE CASCADE,
    CONSTRAINT uk_vendor_security_step_key
        UNIQUE (vendor_config_id, direction, step_key),
    CONSTRAINT uk_vendor_security_step_sort
        UNIQUE (vendor_config_id, direction, sort_no),
    CONSTRAINT ck_vendor_security_step_direction
        CHECK (direction IN ('REQUEST', 'RESPONSE'))
);

CREATE INDEX IF NOT EXISTS idx_vendor_security_step_config
    ON vendor_interface_security_step(vendor_config_id, direction, sort_no);

CREATE TABLE IF NOT EXISTS vendor_interface_security_version (
    id BIGSERIAL PRIMARY KEY,
    vendor_config_id BIGINT NOT NULL,
    version_no INTEGER NOT NULL,
    config_snapshot JSONB NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vendor_security_version_config
        FOREIGN KEY (vendor_config_id) REFERENCES vendor_config(id) ON DELETE CASCADE,
    CONSTRAINT uk_vendor_security_version
        UNIQUE (vendor_config_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_vendor_security_version_config
    ON vendor_interface_security_version(vendor_config_id, version_no DESC);

-- 将旧签名配置迁移为等价的可排序请求流水线，运行时仍保留旧字段回退。
INSERT INTO vendor_interface_security_step
    (vendor_config_id, step_key, direction, step_type, step_name, sort_no, enabled, config_json)
SELECT id,
       'legacy-canonical',
       'REQUEST',
       'CANONICALIZE',
       '旧配置参数规范化',
       100,
       TRUE,
       '{"inputFrom":"PARAMS","fieldOrder":"KEY_ASC","nullPolicy":"IGNORE","pairSeparator":"&","keyValueSeparator":"="}'::jsonb
FROM vendor_config
WHERE sign_type IS NOT NULL
  AND sign_type <> ''
  AND UPPER(sign_type) IN ('MD5', 'HMAC_SHA256', 'HMAC-SHA256')
  AND NOT EXISTS (
      SELECT 1 FROM vendor_interface_security_step s WHERE s.vendor_config_id = vendor_config.id
  );

INSERT INTO vendor_interface_security_step
    (vendor_config_id, step_key, direction, step_type, step_name, sort_no, enabled, config_json)
SELECT id,
       'legacy-signature',
       'REQUEST',
       CASE WHEN UPPER(sign_type) IN ('HMAC_SHA256', 'HMAC-SHA256') THEN 'HMAC' ELSE 'DIGEST' END,
       '旧配置签名',
       200,
       TRUE,
       CASE
           WHEN UPPER(sign_type) IN ('HMAC_SHA256', 'HMAC-SHA256')
               THEN '{"inputFrom":"legacy-canonical","algorithm":"HMAC_SHA256","secretRef":"vendor.secretKey","outputEncoding":"HEX_LOWER"}'::jsonb
           ELSE '{"inputFrom":"legacy-canonical","algorithm":"MD5","secretRef":"vendor.secretKey","secretPlacement":"SUFFIX","outputEncoding":"HEX_LOWER"}'::jsonb
       END
FROM vendor_config
WHERE sign_type IS NOT NULL
  AND sign_type <> ''
  AND UPPER(sign_type) IN ('MD5', 'HMAC_SHA256', 'HMAC-SHA256')
  AND EXISTS (
      SELECT 1 FROM vendor_interface_security_step s
      WHERE s.vendor_config_id = vendor_config.id AND s.step_key = 'legacy-canonical'
  )
  AND NOT EXISTS (
      SELECT 1 FROM vendor_interface_security_step s
      WHERE s.vendor_config_id = vendor_config.id AND s.step_key = 'legacy-signature'
  );

INSERT INTO vendor_interface_security_step
    (vendor_config_id, step_key, direction, step_type, step_name, sort_no, enabled, config_json)
SELECT id,
       'legacy-inject-signature',
       'REQUEST',
       'INJECT',
       '写入签名参数',
       300,
       TRUE,
       '{"inputFrom":"legacy-signature","location":"PARAM","fieldName":"sign"}'::jsonb
FROM vendor_config
WHERE sign_type IS NOT NULL
  AND sign_type <> ''
  AND UPPER(sign_type) IN ('MD5', 'HMAC_SHA256', 'HMAC-SHA256')
  AND EXISTS (
      SELECT 1 FROM vendor_interface_security_step s
      WHERE s.vendor_config_id = vendor_config.id AND s.step_key = 'legacy-signature'
  )
  AND NOT EXISTS (
      SELECT 1 FROM vendor_interface_security_step s
      WHERE s.vendor_config_id = vendor_config.id AND s.step_key = 'legacy-inject-signature'
  );

INSERT INTO vendor_interface_security_version (vendor_config_id, version_no, config_snapshot)
SELECT c.id,
       1,
       jsonb_agg(jsonb_build_object(
           'id', s.id,
           'stepKey', s.step_key,
           'direction', s.direction,
           'stepType', s.step_type,
           'stepName', s.step_name,
           'sortNo', s.sort_no,
           'enabled', s.enabled,
           'config', s.config_json
       ) ORDER BY s.direction, s.sort_no)
FROM vendor_config c
JOIN vendor_interface_security_step s ON s.vendor_config_id = c.id
WHERE c.security_version = 0
  AND NOT EXISTS (
      SELECT 1 FROM vendor_interface_security_version v
      WHERE v.vendor_config_id = c.id AND v.version_no = 1
  )
GROUP BY c.id;

UPDATE vendor_config c
SET security_version = 1
WHERE security_version = 0
  AND EXISTS (SELECT 1 FROM vendor_interface_security_step s WHERE s.vendor_config_id = c.id);
