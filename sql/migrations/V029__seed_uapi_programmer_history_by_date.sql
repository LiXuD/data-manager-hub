-- 接入 UAPI「程序员历史事件（指定日期）」真实外部数据源。
-- 官方文档: https://uapis.cn/docs/api-reference/get-history-programmer
-- 外部接口: GET https://uapis.cn/api/v1/history/programmer?month={month}&day={day}

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM vendor_info
        WHERE vendor_code = 'uapi'
          AND status = 'active'
          AND COALESCE(deleted, false) = false
    ) THEN
        RAISE EXCEPTION '缺少已启用的 UAPI 厂商，请先应用 V017';
    END IF;

    IF EXISTS (
        SELECT 1 FROM data_type
        WHERE data_type_code = 'programmer_history_by_date'
    ) OR EXISTS (
        SELECT 1 FROM api_interface
        WHERE interface_code = 'PROGRAMMER_HISTORY_BY_DATE'
    ) OR EXISTS (
        SELECT 1 FROM billing_plan
        WHERE plan_code = 'UAPI-PROGRAMMER-HISTORY-BY-DATE'
    ) THEN
        RAISE EXCEPTION '检测到与 V029 冲突的手工配置，请先核对并迁移现有数据';
    END IF;
END $$;

INSERT INTO data_type (
    data_type_code, data_type_name, data_category, description,
    pricing_model, unit_price, status
)
VALUES (
    'programmer_history_by_date', '指定日期程序员历史事件', 'public',
    '按月日查询程序员、计算机与科技相关历史事件，数据来源 UAPI',
    'per_call', 0, 'active'
);

INSERT INTO api_interface (
    interface_code, interface_name, data_type_id, vendor_id,
    path, description, sort, status
)
SELECT
    'PROGRAMMER_HISTORY_BY_DATE', '指定日期的程序员历史事件',
    dt.id, vi.id, '/api/v1/history/programmer',
    '输入月份和日期，获取当天发生的程序员、计算机与科技相关历史事件',
    101, 'active'
FROM data_type dt
JOIN vendor_info vi ON vi.vendor_code = 'uapi'
WHERE dt.data_type_code = 'programmer_history_by_date';

INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id,
    api_url, method, timeout, retry_count,
    circuit_threshold, circuit_timeout, auth_type, status
)
SELECT
    vi.id, dt.id, dt.data_type_code, ai.id,
    'https://uapis.cn/api/v1/history/programmer', 'GET', 10000, 1,
    5, 60, 'NONE', 'active'
FROM vendor_info vi
JOIN data_type dt ON dt.data_type_code = 'programmer_history_by_date'
JOIN api_interface ai ON ai.interface_code = 'PROGRAMMER_HISTORY_BY_DATE'
WHERE vi.vendor_code = 'uapi';

-- 官方文档定义的必填查询参数和边界。
INSERT INTO interface_param (
    interface_id, direction, param_name, description, param_type,
    required, sort, example_value, constraint_config
)
SELECT
    ai.id, 'REQUEST', field.param_name, field.description, 'integer',
    true, field.sort, field.example_value, field.constraint_config::JSONB
FROM api_interface ai
CROSS JOIN (VALUES
    ('month', '月份，整数 1-12', 10, '4', '{"minimum":1,"maximum":12}'),
    ('day', '日期，整数 1-31', 20, '4', '{"minimum":1,"maximum":31}')
) AS field(param_name, description, sort, example_value, constraint_config)
WHERE ai.interface_code = 'PROGRAMMER_HISTORY_BY_DATE';

-- 响应契约根字段。
INSERT INTO interface_param (
    interface_id, direction, param_name, description, param_type,
    array_item_type, required, sort, example_value
)
SELECT
    ai.id, 'RESPONSE', field.param_name, field.description, field.param_type,
    field.array_item_type, true, field.sort, field.example_value
FROM api_interface ai
CROSS JOIN (VALUES
    ('message', '响应消息', 'string', NULL::VARCHAR, 10, '获取成功'),
    ('date', '查询日期，格式 MM-dd', 'string', NULL::VARCHAR, 20, '04-04'),
    ('events', '指定日期的程序员历史事件', 'array', 'object'::VARCHAR, 30, NULL)
) AS field(param_name, description, param_type, array_item_type, sort, example_value)
WHERE ai.interface_code = 'PROGRAMMER_HISTORY_BY_DATE';

-- events 数组元素字段。
INSERT INTO interface_param (
    interface_id, direction, parent_id, param_name, description, param_type,
    array_item_type, required, sort, example_value
)
SELECT
    ai.id, 'RESPONSE', events.id, field.param_name, field.description,
    field.param_type, field.array_item_type, field.required, field.sort, field.example_value
FROM api_interface ai
JOIN interface_param events
  ON events.interface_id = ai.id
 AND events.direction = 'RESPONSE'
 AND events.parent_id IS NULL
 AND events.param_name = 'events'
CROSS JOIN (VALUES
    ('year', '事件年份', 'integer', NULL::VARCHAR, true, 10, '1975'),
    ('month', '事件月份；上游未知时可能为 0', 'integer', NULL::VARCHAR, false, 20, '0'),
    ('day', '事件日期；上游未知时可能为 0', 'integer', NULL::VARCHAR, false, 30, '0'),
    ('title', '事件标题', 'string', NULL::VARCHAR, true, 40, '微软公司成立。'),
    ('description', '事件描述', 'string', NULL::VARCHAR, true, 50, NULL),
    ('category', '事件分类', 'string', NULL::VARCHAR, true, 60, '商业'),
    ('tags', '事件标签', 'array', 'string'::VARCHAR, false, 70, NULL),
    ('importance', '重要性评分', 'integer', NULL::VARCHAR, true, 80, '10'),
    ('source', '数据来源', 'string', NULL::VARCHAR, true, 90, '历史数据')
) AS field(param_name, description, param_type, array_item_type, required, sort, example_value)
WHERE ai.interface_code = 'PROGRAMMER_HISTORY_BY_DATE';

-- UAPI 免费端点的零元计费方案，保证 Billing 失败关闭链路可以正常完成。
INSERT INTO billing_plan (
    plan_code, version, plan_name, vendor_id, vendor_code, vendor_name,
    interface_id, interface_code, interface_name, template_code,
    accounting_purpose, currency, timezone, settlement_cycle,
    pricing_config, metering_config, adjustment_config, status, effective_from,
    created_at, updated_at
)
SELECT
    'UAPI-PROGRAMMER-HISTORY-BY-DATE', 1, 'UAPI 指定日期程序员历史免费方案',
    vi.id, vi.vendor_code, vi.vendor_name, ai.id, ai.interface_code, ai.interface_name,
    'PER_CALL', 'VENDOR_PAYABLE', 'CNY', 'Asia/Shanghai', 'MONTH',
    '{"unitPrice":0,"packageFee":0,"includedUnits":0,"overageUnitPrice":0,"tierMode":"GRADUATED","durationUnit":"SECOND","durationRounding":"CEILING","carryOver":false}',
    '{"logic":"AND","conditions":[],"quantity":{"type":"FIXED","fixedValue":1,"unit":"CALL"},"missingFieldPolicy":"PENDING_REVIEW","cacheBillingPolicy":"FREE","aggregationScope":"VENDOR_INTERFACE"}',
    '{"noChargeOnFailure":true,"requireValidContract":false,"slaEnabled":false}',
    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM vendor_info vi
JOIN api_interface ai ON ai.vendor_id = vi.id
WHERE vi.vendor_code = 'uapi'
  AND ai.interface_code = 'PROGRAMMER_HISTORY_BY_DATE';
