\set ON_ERROR_STOP on

-- This file seeds only business data for an isolated dev-MVP database.
-- Connector responses live in the HTTPS fixture, never in formal migrations.

INSERT INTO tenant_info (
    tenant_code, tenant_name, tenant_type, status, max_api_keys, max_callers, deleted
)
VALUES (
    'dev-mvp-tenant-' || :'run_token',
    'Dev MVP Tenant', 'enterprise', 'active', 50, 50, FALSE
);

INSERT INTO user_info (
    username, nickname, password, tenant_id, status, deleted
)
SELECT 'dev-mvp-admin-' || :'run_token', 'Dev MVP Admin', :'password_hash',
       id, 'active', FALSE
FROM tenant_info
WHERE tenant_code = 'dev-mvp-tenant-' || :'run_token';

INSERT INTO user_info (
    username, nickname, password, tenant_id, status, deleted
)
SELECT 'dev-mvp-applicant-' || :'run_token', 'Dev MVP Applicant', :'password_hash',
       id, 'active', FALSE
FROM tenant_info
WHERE tenant_code = 'dev-mvp-tenant-' || :'run_token';

INSERT INTO user_role (user_id, role_id, created_by, deleted)
SELECT u.id, r.id, u.id, FALSE
FROM user_info u
JOIN role_info r ON r.role_code = 'admin'
WHERE u.username = 'dev-mvp-admin-' || :'run_token'
  AND r.status = 'active' AND r.deleted = FALSE
ON CONFLICT (user_id, role_id) DO UPDATE SET deleted = FALSE;

INSERT INTO user_role (user_id, role_id, created_by, deleted)
SELECT u.id, r.id, u.id, FALSE
FROM user_info u
JOIN role_info r ON r.role_code = 'user'
WHERE u.username = 'dev-mvp-applicant-' || :'run_token'
  AND r.status = 'active' AND r.deleted = FALSE
ON CONFLICT (user_id, role_id) DO UPDATE SET deleted = FALSE;

INSERT INTO user_role (user_id, role_id, created_by, deleted)
SELECT u.id, r.id, u.id, FALSE
FROM user_info u
JOIN role_info r ON r.role_code = 'api_interface_approver'
WHERE u.username = 'dev-mvp-admin-' || :'run_token'
  AND r.status = 'active' AND r.deleted = FALSE
ON CONFLICT (user_id, role_id) DO UPDATE SET deleted = FALSE;

INSERT INTO vendor_info (
    vendor_code, vendor_name, vendor_type, description, status, deleted
)
VALUES
    ('dev-mvp-risk-primary-' || :'run_token', '工商信息主厂商', 'business-data',
     'Dev MVP 单次 HTTP 主厂商', 'active', FALSE),
    ('dev-mvp-risk-backup-' || :'run_token', '工商信息备厂商', 'business-data',
     'Dev MVP 主备切换备厂商', 'active', FALSE),
    ('dev-mvp-personal-' || :'run_token', '个人信息厂商', 'personal-data',
     'Dev MVP Token 加业务请求厂商', 'active', FALSE);

INSERT INTO data_type (
    data_type_code, data_type_name, data_category, description,
    pricing_model, unit_price, status, deleted
)
VALUES
    ('business-registration-' || :'run_token', '工商信息', 'enterprise',
     '企业工商登记信息', 'per_call', 0.25, 'active', FALSE),
    ('personal-information-' || :'run_token', '个人信息', 'personal',
     '个人基础信息', 'per_call', 0.50, 'active', FALSE);

INSERT INTO api_interface (
    interface_code, interface_name, data_type_id, vendor_id, path, description,
    request_schema, response_schema, sort, status, deleted
)
SELECT 'DEV_MVP_BUSINESS_' || :'run_token', '工商信息查询', dt.id, v.id,
       '/dev-mvp/business-registration', 'Dev MVP 工商信息查询',
       '{"type":"object"}'::jsonb, '{"type":"object"}'::jsonb, 1, 'inactive', FALSE
FROM data_type dt
JOIN vendor_info v ON v.vendor_code = 'dev-mvp-risk-primary-' || :'run_token'
WHERE dt.data_type_code = 'business-registration-' || :'run_token';

INSERT INTO api_interface (
    interface_code, interface_name, data_type_id, vendor_id, path, description,
    request_schema, response_schema, sort, status, deleted
)
SELECT 'DEV_MVP_PERSONAL_' || :'run_token', '个人信息查询', dt.id, v.id,
       '/dev-mvp/personal-information', 'Dev MVP 个人信息查询',
       '{"type":"object"}'::jsonb, '{"type":"object"}'::jsonb, 2, 'inactive', FALSE
FROM data_type dt
JOIN vendor_info v ON v.vendor_code = 'dev-mvp-personal-' || :'run_token'
WHERE dt.data_type_code = 'personal-information-' || :'run_token';

INSERT INTO interface_param (
    interface_id, param_name, description, param_type, required, sort, direction
)
SELECT ai.id, 'companyName', '企业名称', 'string', TRUE, 0, 'REQUEST'
FROM api_interface ai
WHERE ai.interface_code = 'DEV_MVP_BUSINESS_' || :'run_token';

INSERT INTO interface_param (
    interface_id, param_name, description, param_type, required, sort, direction
)
SELECT ai.id, 'idCard', '身份证号码', 'string', TRUE, 0, 'REQUEST'
FROM api_interface ai
WHERE ai.interface_code = 'DEV_MVP_PERSONAL_' || :'run_token';

INSERT INTO interface_param (
    interface_id, param_name, description, param_type, required, sort, direction
)
SELECT ai.id, 'success', '厂商处理是否成功', 'boolean', TRUE, 0, 'RESPONSE'
FROM api_interface ai
WHERE ai.interface_code IN (
    'DEV_MVP_BUSINESS_' || :'run_token', 'DEV_MVP_PERSONAL_' || :'run_token'
);

INSERT INTO interface_param (
    interface_id, param_name, description, param_type, required, sort, direction
)
SELECT ai.id, 'fixture', '验收夹具标识', 'string', TRUE, 1, 'RESPONSE'
FROM api_interface ai
WHERE ai.interface_code IN (
    'DEV_MVP_BUSINESS_' || :'run_token', 'DEV_MVP_PERSONAL_' || :'run_token'
);

INSERT INTO interface_param (
    interface_id, param_name, description, param_type, required, sort, direction
)
SELECT ai.id, 'received', '厂商收到的原始参数', 'object', FALSE, 2, 'RESPONSE'
FROM api_interface ai
WHERE ai.interface_code IN (
    'DEV_MVP_BUSINESS_' || :'run_token', 'DEV_MVP_PERSONAL_' || :'run_token'
);

INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id,
    timeout, retry_count, circuit_threshold, circuit_timeout,
    fallback_vendor_id, security_version, status, deleted,
    runtime_mode, connector_version
)
SELECT primary_vendor.id, dt.id, dt.data_type_code, ai.id,
       5000, 0, 1, 60, backup_vendor.id, 0, 'inactive', FALSE, 'PLUGIN', 0
FROM vendor_info primary_vendor
JOIN vendor_info backup_vendor
  ON backup_vendor.vendor_code = 'dev-mvp-risk-backup-' || :'run_token'
JOIN data_type dt ON dt.data_type_code = 'business-registration-' || :'run_token'
JOIN api_interface ai ON ai.interface_code = 'DEV_MVP_BUSINESS_' || :'run_token'
WHERE primary_vendor.vendor_code = 'dev-mvp-risk-primary-' || :'run_token';

INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id,
    timeout, retry_count, circuit_threshold, circuit_timeout,
    security_version, status, deleted, runtime_mode, connector_version
)
SELECT backup_vendor.id, dt.id, dt.data_type_code, ai.id,
       5000, 0, 1, 60, 0, 'inactive', FALSE, 'PLUGIN', 0
FROM vendor_info backup_vendor
JOIN data_type dt ON dt.data_type_code = 'business-registration-' || :'run_token'
JOIN api_interface ai ON ai.interface_code = 'DEV_MVP_BUSINESS_' || :'run_token'
WHERE backup_vendor.vendor_code = 'dev-mvp-risk-backup-' || :'run_token';

INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id,
    timeout, retry_count, circuit_threshold, circuit_timeout,
    security_version, status, deleted, runtime_mode, connector_version
)
SELECT personal_vendor.id, dt.id, dt.data_type_code, ai.id,
       5000, 0, 1, 60, 0, 'inactive', FALSE, 'PLUGIN', 0
FROM vendor_info personal_vendor
JOIN data_type dt ON dt.data_type_code = 'personal-information-' || :'run_token'
JOIN api_interface ai ON ai.interface_code = 'DEV_MVP_PERSONAL_' || :'run_token'
WHERE personal_vendor.vendor_code = 'dev-mvp-personal-' || :'run_token';

INSERT INTO caller_info (
    caller_code, caller_name, tenant_id, caller_type, description, status, deleted
)
SELECT 'dev-mvp-risk-control-' || :'run_token', '风控系统', t.id, 'system',
       'Dev MVP 风控调用系统', 'active', FALSE
FROM tenant_info t
WHERE t.tenant_code = 'dev-mvp-tenant-' || :'run_token';

INSERT INTO caller_info (
    caller_code, caller_name, tenant_id, caller_type, description, status, deleted
)
SELECT 'dev-mvp-credit-' || :'run_token', '信贷系统', t.id, 'system',
       'Dev MVP 信贷调用系统', 'active', FALSE
FROM tenant_info t
WHERE t.tenant_code = 'dev-mvp-tenant-' || :'run_token';

INSERT INTO user_caller (user_id, caller_id, created_by)
SELECT u.id, c.id, u.id
FROM user_info u
JOIN caller_info c ON c.caller_code IN (
    'dev-mvp-risk-control-' || :'run_token', 'dev-mvp-credit-' || :'run_token'
)
WHERE u.username = 'dev-mvp-applicant-' || :'run_token'
ON CONFLICT (user_id, caller_id) DO NOTHING;

INSERT INTO call_scene (scene_code, scene_name, description, status, deleted)
VALUES (
    'dev-mvp-scene-' || :'run_token', 'Dev MVP 业务验收场景',
    '风控与信贷系统共用的开发验收调用场景', 'active', FALSE
);

INSERT INTO billing_plan (
    plan_code, version, plan_name, vendor_id, vendor_code, vendor_name,
    interface_id, interface_code, interface_name, template_code,
    accounting_purpose, currency, timezone, settlement_cycle,
    pricing_config, metering_config, adjustment_config, status, effective_from,
    created_at, updated_at
)
SELECT 'DEV-MVP-PRIMARY-' || :'run_token', 1, 'Dev MVP 工商主厂商按次方案',
       v.id, v.vendor_code, v.vendor_name, ai.id, ai.interface_code, ai.interface_name,
       'PER_CALL', 'VENDOR_PAYABLE', 'CNY', 'Asia/Shanghai', 'MONTH',
       '{"unitPrice":0.25,"packageFee":0,"includedUnits":0,"overageUnitPrice":0,"tierMode":"GRADUATED","durationUnit":"SECOND","durationRounding":"CEILING","carryOver":false}',
       '{"logic":"AND","conditions":[],"quantity":{"type":"FIXED","fixedValue":1,"unit":"CALL"},"missingFieldPolicy":"PENDING_REVIEW","cacheBillingPolicy":"FREE","aggregationScope":"VENDOR_INTERFACE"}',
       '{"noChargeOnFailure":true,"requireValidContract":false,"slaEnabled":false}',
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM vendor_info v
JOIN api_interface ai ON ai.interface_code = 'DEV_MVP_BUSINESS_' || :'run_token'
WHERE v.vendor_code = 'dev-mvp-risk-primary-' || :'run_token';

INSERT INTO billing_plan (
    plan_code, version, plan_name, vendor_id, vendor_code, vendor_name,
    interface_id, interface_code, interface_name, template_code,
    accounting_purpose, currency, timezone, settlement_cycle,
    pricing_config, metering_config, adjustment_config, status, effective_from,
    created_at, updated_at
)
SELECT 'DEV-MVP-BACKUP-' || :'run_token', 1, 'Dev MVP 工商备厂商按次方案',
       v.id, v.vendor_code, v.vendor_name, ai.id, ai.interface_code, ai.interface_name,
       'PER_CALL', 'VENDOR_PAYABLE', 'CNY', 'Asia/Shanghai', 'MONTH',
       '{"unitPrice":0.25,"packageFee":0,"includedUnits":0,"overageUnitPrice":0,"tierMode":"GRADUATED","durationUnit":"SECOND","durationRounding":"CEILING","carryOver":false}',
       '{"logic":"AND","conditions":[],"quantity":{"type":"FIXED","fixedValue":1,"unit":"CALL"},"missingFieldPolicy":"PENDING_REVIEW","cacheBillingPolicy":"FREE","aggregationScope":"VENDOR_INTERFACE"}',
       '{"noChargeOnFailure":true,"requireValidContract":false,"slaEnabled":false}',
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM vendor_info v
JOIN api_interface ai ON ai.interface_code = 'DEV_MVP_BUSINESS_' || :'run_token'
WHERE v.vendor_code = 'dev-mvp-risk-backup-' || :'run_token';

INSERT INTO billing_plan (
    plan_code, version, plan_name, vendor_id, vendor_code, vendor_name,
    interface_id, interface_code, interface_name, template_code,
    accounting_purpose, currency, timezone, settlement_cycle,
    pricing_config, metering_config, adjustment_config, status, effective_from,
    created_at, updated_at
)
SELECT 'DEV-MVP-PERSONAL-' || :'run_token', 1, 'Dev MVP 个人厂商按次方案',
       v.id, v.vendor_code, v.vendor_name, ai.id, ai.interface_code, ai.interface_name,
       'PER_CALL', 'VENDOR_PAYABLE', 'CNY', 'Asia/Shanghai', 'MONTH',
       '{"unitPrice":0.50,"packageFee":0,"includedUnits":0,"overageUnitPrice":0,"tierMode":"GRADUATED","durationUnit":"SECOND","durationRounding":"CEILING","carryOver":false}',
       '{"logic":"AND","conditions":[],"quantity":{"type":"FIXED","fixedValue":1,"unit":"CALL"},"missingFieldPolicy":"PENDING_REVIEW","cacheBillingPolicy":"FREE","aggregationScope":"VENDOR_INTERFACE"}',
       '{"noChargeOnFailure":true,"requireValidContract":false,"slaEnabled":false}',
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM vendor_info v
JOIN api_interface ai ON ai.interface_code = 'DEV_MVP_PERSONAL_' || :'run_token'
WHERE v.vendor_code = 'dev-mvp-personal-' || :'run_token';

WITH counts AS (
    SELECT
        (SELECT COUNT(*) FROM vendor_info
         WHERE vendor_code LIKE 'dev-mvp-%-' || :'run_token') AS vendors,
        (SELECT COUNT(*) FROM data_type
         WHERE data_type_code IN (
             'business-registration-' || :'run_token',
             'personal-information-' || :'run_token')) AS data_types,
        (SELECT COUNT(*) FROM api_interface
         WHERE interface_code IN (
             'DEV_MVP_BUSINESS_' || :'run_token',
             'DEV_MVP_PERSONAL_' || :'run_token')) AS interfaces,
        (SELECT COUNT(*) FROM vendor_config vc
         JOIN api_interface ai ON ai.id = vc.interface_id
         WHERE ai.interface_code IN (
             'DEV_MVP_BUSINESS_' || :'run_token',
             'DEV_MVP_PERSONAL_' || :'run_token')) AS configs,
        (SELECT COUNT(*) FROM caller_info
         WHERE caller_code IN (
             'dev-mvp-risk-control-' || :'run_token',
             'dev-mvp-credit-' || :'run_token')) AS callers,
        (SELECT COUNT(*) FROM billing_plan
         WHERE plan_code LIKE 'DEV-MVP-%-' || :'run_token') AS plans
)
SELECT vendors = 3 AND data_types = 2 AND interfaces = 2
       AND configs = 3 AND callers = 2 AND plans = 3
       AS seed_cardinality_ok
FROM counts
\gset
\if :seed_cardinality_ok
\else
\echo 'dev MVP seed cardinality mismatch'
\quit 1
\endif
