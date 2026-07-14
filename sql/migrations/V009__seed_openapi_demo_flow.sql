-- V009__seed_openapi_demo_flow.sql
-- Idempotent demo configuration for a complete local OpenAPI invocation path.

INSERT INTO vendor_info (vendor_code, vendor_name, vendor_type, status, description)
SELECT 'mock_vendor', '本地模拟厂商', 'mock', 'active', '用于本地端到端联调的模拟厂商'
WHERE NOT EXISTS (SELECT 1 FROM vendor_info WHERE vendor_code = 'mock_vendor');

INSERT INTO data_type (data_type_code, data_type_name, data_category, description, pricing_model, unit_price, status)
SELECT 'company_info', '工商信息', 'enterprise', '企业工商基础信息', 'per_call', 0.30, 'active'
WHERE NOT EXISTS (SELECT 1 FROM data_type WHERE data_type_code = 'company_info');

INSERT INTO api_interface (interface_code, interface_name, data_type_id, path, description, status)
SELECT 'COMPANY_BASE', '企业基本信息', dt.id, '/company/base', '查询企业基本工商信息', 'active'
FROM data_type dt
WHERE dt.data_type_code = 'company_info'
  AND NOT EXISTS (SELECT 1 FROM api_interface WHERE interface_code = 'COMPANY_BASE');

INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, api_url, method, timeout, retry_count,
    circuit_threshold, circuit_timeout, response_mapping, status, interface_id
)
SELECT v.id, dt.id, dt.data_type_code, 'mock://company/base', 'POST', 1000, 0,
       10, 30000,
       '{"matched":"matched","riskLevel":"riskLevel","score":"score","vendorCode":"vendorCode","dataTypeCode":"dataTypeCode"}',
       'active', ai.id
FROM vendor_info v
JOIN data_type dt ON dt.data_type_code = 'company_info'
JOIN api_interface ai ON ai.interface_code = 'COMPANY_BASE'
WHERE v.vendor_code = 'mock_vendor'
  AND NOT EXISTS (
      SELECT 1 FROM vendor_config vc
      WHERE vc.vendor_id = v.id AND vc.data_type_id = dt.id
  );

INSERT INTO caller_info (tenant_id, caller_code, caller_name, caller_type, status, description)
SELECT 1, 'demo-caller', '演示调用方', 'system', 'active', '用于本地端到端联调'
WHERE NOT EXISTS (SELECT 1 FROM caller_info WHERE caller_code = 'demo-caller');

INSERT INTO caller_product (caller_id, product_code, product_name, cache_scope, status)
SELECT c.id, 'loan-risk', '信贷风控', 'GLOBAL', 'active'
FROM caller_info c
WHERE c.caller_code = 'demo-caller'
  AND NOT EXISTS (
      SELECT 1 FROM caller_product cp
      WHERE cp.caller_id = c.id AND cp.product_code = 'loan-risk'
  );

INSERT INTO call_scene (scene_code, scene_name, status, description)
SELECT 'pre-loan-review', '贷前审批', 'active', '贷前审批风控查询'
WHERE NOT EXISTS (SELECT 1 FROM call_scene WHERE scene_code = 'pre-loan-review');

INSERT INTO api_key (caller_id, key_name, api_key, api_secret, rate_limit, quota_limit, quota_used, status, expire_time)
SELECT c.id, 'demo', 'dp_demo_openapi_key', 'demo_openapi_secret', 100, 100000, 0, 'active', CURRENT_TIMESTAMP + INTERVAL '1 year'
FROM caller_info c
WHERE c.caller_code = 'demo-caller'
  AND NOT EXISTS (SELECT 1 FROM api_key WHERE api_key = 'dp_demo_openapi_key');

INSERT INTO api_key_product (api_key_id, product_id)
SELECT ak.id, cp.id
FROM api_key ak
JOIN caller_product cp ON cp.caller_id = ak.caller_id AND cp.product_code = 'loan-risk'
WHERE ak.api_key = 'dp_demo_openapi_key'
  AND NOT EXISTS (
      SELECT 1 FROM api_key_product akp
      WHERE akp.api_key_id = ak.id AND akp.product_id = cp.id
  );

INSERT INTO api_key_interface (api_key_id, interface_id)
SELECT ak.id, ai.id
FROM api_key ak
JOIN api_interface ai ON ai.interface_code = 'COMPANY_BASE'
WHERE ak.api_key = 'dp_demo_openapi_key'
  AND NOT EXISTS (
      SELECT 1 FROM api_key_interface aki
      WHERE aki.api_key_id = ak.id AND aki.interface_id = ai.id
  );

INSERT INTO interface_param (interface_id, param_name, description, param_type, required, sort)
SELECT ai.id, 'companyName', '企业名称', 'string', true, 1
FROM api_interface ai
WHERE ai.interface_code = 'COMPANY_BASE'
  AND NOT EXISTS (
      SELECT 1 FROM interface_param ip
      WHERE ip.interface_id = ai.id AND ip.param_name = 'companyName'
  );
