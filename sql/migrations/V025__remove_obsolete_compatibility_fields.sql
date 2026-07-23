-- 删除已由结构化契约与安全流水线取代的兼容字段。
-- 迁移会在发现无法无损转换的数据时中止，避免静默丢失行为。

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_info
        WHERE password !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
    ) THEN
        RAISE EXCEPTION '仍存在非 BCrypt 用户密码；请先完成密码重置或离线迁移';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM vendor_config_extended
        WHERE is_encrypted = TRUE
          AND config_value IS NOT NULL
          AND config_value !~ '^v1:[1-9][0-9]*:.+$'
    ) THEN
        RAISE EXCEPTION '仍存在标记为加密但实际为明文的厂商扩展配置；请先完成加密迁移';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM vendor_config c
        WHERE COALESCE(c.sign_type, '') <> ''
          AND NOT EXISTS (
              SELECT 1
              FROM vendor_interface_security_step s
              WHERE s.vendor_config_id = c.id
                AND s.direction = 'REQUEST'
                AND s.enabled = TRUE
          )
    ) THEN
        RAISE EXCEPTION '仍存在未迁移到请求安全流水线的厂商签名配置';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM vendor_config
        WHERE COALESCE(encrypt_type, '') <> ''
    ) THEN
        RAISE EXCEPTION '仍存在未迁移到安全流水线的厂商加密配置';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM interface_param
        WHERE COALESCE(validation_rule, '') <> ''
          AND validation_rule <> 'not_empty'
          AND validation_rule !~ '^regex:.+$'
          AND validation_rule !~ '^range:([-+]?[0-9]+(?:\.[0-9]+)?)-([-+]?[0-9]+(?:\.[0-9]+)?)$'
    ) THEN
        RAISE EXCEPTION 'interface_param 中存在无法无损迁移的 validation_rule';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM api_interface api
        WHERE api.request_schema IS NOT NULL
          AND api.request_schema NOT IN (
              '{}'::jsonb,
              'null'::jsonb,
              '{"type":"object","additionalProperties":true,"properties":{}}'::jsonb
          )
          AND NOT EXISTS (
              SELECT 1
              FROM interface_param param
              WHERE param.interface_id = api.id
                AND COALESCE(param.direction, 'REQUEST') = 'REQUEST'
          )
    ) THEN
        RAISE EXCEPTION '仍存在只有请求 Schema 快照、没有结构化字段的接口；请先转换为 interface_param';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM api_interface api
        WHERE api.response_schema IS NOT NULL
          AND api.response_schema NOT IN (
              '{}'::jsonb,
              'null'::jsonb,
              '{"type":"object","additionalProperties":true,"properties":{}}'::jsonb
          )
          AND NOT EXISTS (
              SELECT 1
              FROM interface_param param
              WHERE param.interface_id = api.id
                AND param.direction = 'RESPONSE'
          )
    ) THEN
        RAISE EXCEPTION '仍存在只有响应 Schema 快照、没有结构化字段的接口；请先转换为 interface_param';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM vendor_interface_security_step old_step
        JOIN vendor_interface_security_step new_step
          ON new_step.vendor_config_id = old_step.vendor_config_id
         AND new_step.direction = old_step.direction
        WHERE old_step.step_key IN ('legacy-canonical', 'legacy-signature', 'legacy-inject-signature')
          AND new_step.step_key IN ('migrated-canonical', 'migrated-signature', 'migrated-inject-signature')
    ) THEN
        RAISE EXCEPTION '安全流水线迁移步骤重命名发生冲突';
    END IF;
END $$;

UPDATE interface_param
SET constraint_config =
        jsonb_build_object('pattern', substring(validation_rule FROM 7))
        || COALESCE(constraint_config, '{}'::jsonb)
WHERE validation_rule ~ '^regex:.+$';

WITH range_rules AS (
    SELECT id,
           regexp_match(
               validation_rule,
               '^range:([-+]?[0-9]+(?:\.[0-9]+)?)-([-+]?[0-9]+(?:\.[0-9]+)?)$'
           ) AS bounds
    FROM interface_param
    WHERE validation_rule ~ '^range:'
)
UPDATE interface_param target
SET constraint_config =
        jsonb_build_object(
            'minimum', (range_rules.bounds[1])::numeric,
            'maximum', (range_rules.bounds[2])::numeric
        ) || COALESCE(target.constraint_config, '{}'::jsonb)
FROM range_rules
WHERE target.id = range_rules.id;

UPDATE interface_param
SET constraint_config =
        jsonb_build_object('minLength', 1)
        || COALESCE(constraint_config, '{}'::jsonb)
WHERE validation_rule = 'not_empty';

UPDATE vendor_interface_security_step
SET config_json = jsonb_set(
        config_json,
        '{inputFrom}',
        to_jsonb(CASE config_json ->> 'inputFrom'
            WHEN 'legacy-canonical' THEN 'migrated-canonical'
            WHEN 'legacy-signature' THEN 'migrated-signature'
            ELSE config_json ->> 'inputFrom'
        END),
        FALSE
    )
WHERE config_json ->> 'inputFrom' IN ('legacy-canonical', 'legacy-signature');

UPDATE vendor_interface_security_step
SET step_key = CASE step_key
        WHEN 'legacy-canonical' THEN 'migrated-canonical'
        WHEN 'legacy-signature' THEN 'migrated-signature'
        WHEN 'legacy-inject-signature' THEN 'migrated-inject-signature'
        ELSE step_key
    END,
    step_name = CASE step_key
        WHEN 'legacy-canonical' THEN '迁移配置参数规范化'
        WHEN 'legacy-signature' THEN '迁移配置签名'
        ELSE step_name
    END
WHERE step_key IN ('legacy-canonical', 'legacy-signature', 'legacy-inject-signature');

ALTER TABLE interface_param
    DROP COLUMN IF EXISTS validation_rule;

ALTER TABLE vendor_config
    DROP COLUMN IF EXISTS sign_type,
    DROP COLUMN IF EXISTS encrypt_type;
