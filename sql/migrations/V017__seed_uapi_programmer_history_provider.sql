-- 接入 UAPI「程序员历史上的今天」真实外部数据源。
-- 官方文档: https://uapis.cn/docs/api-reference/get-history-programmer-today
-- 外部接口: GET https://uapis.cn/api/v1/history/programmer/today

INSERT INTO data_type (
    data_type_code, data_type_name, data_category, description,
    pricing_model, unit_price, status
)
SELECT
    'programmer_history', '程序员历史事件', 'public',
    '程序员历史上的今天，数据来源 UAPI', 'per_call', 0, 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM data_type WHERE data_type_code = 'programmer_history'
);

INSERT INTO vendor_info (
    vendor_code, vendor_name, vendor_type, status, description
)
SELECT
    'uapi', 'UAPI', 'public_data', 'active',
    'UAPI 免费公共 API 平台（https://uapis.cn）'
WHERE NOT EXISTS (
    SELECT 1 FROM vendor_info WHERE vendor_code = 'uapi'
);

INSERT INTO api_interface (
    interface_code, interface_name, data_type_id, vendor_id,
    path, description, sort, status
)
SELECT
    'PROGRAMMER_HISTORY_TODAY', '程序员历史上的今天', dt.id, vi.id,
    '/api/v1/history/programmer/today',
    '获取当天发生的程序员、计算机与科技相关历史事件', 100, 'active'
FROM data_type dt
JOIN vendor_info vi ON vi.vendor_code = 'uapi'
WHERE dt.data_type_code = 'programmer_history'
  AND NOT EXISTS (
      SELECT 1 FROM api_interface WHERE interface_code = 'PROGRAMMER_HISTORY_TODAY'
  );

UPDATE api_interface ai
SET vendor_id = vi.id,
    data_type_id = dt.id,
    path = '/api/v1/history/programmer/today',
    status = 'active',
    updated_at = CURRENT_TIMESTAMP
FROM vendor_info vi, data_type dt
WHERE ai.interface_code = 'PROGRAMMER_HISTORY_TODAY'
  AND vi.vendor_code = 'uapi'
  AND dt.data_type_code = 'programmer_history';

INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id,
    api_url, method, timeout, retry_count,
    circuit_threshold, circuit_timeout, auth_type, status
)
SELECT
    vi.id, dt.id, dt.data_type_code, ai.id,
    'https://uapis.cn/api/v1/history/programmer/today', 'GET', 10000, 1,
    5, 60, 'NONE', 'active'
FROM vendor_info vi
JOIN data_type dt ON dt.data_type_code = 'programmer_history'
JOIN api_interface ai ON ai.interface_code = 'PROGRAMMER_HISTORY_TODAY'
WHERE vi.vendor_code = 'uapi'
  AND NOT EXISTS (
      SELECT 1
      FROM vendor_config vc
      WHERE vc.vendor_id = vi.id AND vc.data_type_id = dt.id
  );

UPDATE vendor_config vc
SET data_type_code = dt.data_type_code,
    interface_id = ai.id,
    api_url = 'https://uapis.cn/api/v1/history/programmer/today',
    method = 'GET',
    timeout = 10000,
    retry_count = 1,
    auth_type = 'NONE',
    status = 'active',
    updated_at = CURRENT_TIMESTAMP
FROM vendor_info vi, data_type dt, api_interface ai
WHERE vc.vendor_id = vi.id
  AND vc.data_type_id = dt.id
  AND vi.vendor_code = 'uapi'
  AND dt.data_type_code = 'programmer_history'
  AND ai.interface_code = 'PROGRAMMER_HISTORY_TODAY';

-- 响应契约根字段。
INSERT INTO interface_param (
    interface_id, direction, param_name, description, param_type,
    array_item_type, required, sort, example_value
)
SELECT ai.id, 'RESPONSE', field.param_name, field.description, field.param_type,
       field.array_item_type, true, field.sort, field.example_value
FROM api_interface ai
CROSS JOIN (VALUES
    ('message', '响应消息', 'string', NULL::VARCHAR, 10, '获取成功'),
    ('date', '日期，格式 MM-dd', 'string', NULL::VARCHAR, 20, '07-20'),
    ('events', '当天的程序员历史事件', 'array', 'object'::VARCHAR, 30, NULL)
) AS field(param_name, description, param_type, array_item_type, sort, example_value)
WHERE ai.interface_code = 'PROGRAMMER_HISTORY_TODAY'
  AND NOT EXISTS (
      SELECT 1
      FROM interface_param ip
      WHERE ip.interface_id = ai.id
        AND ip.direction = 'RESPONSE'
        AND ip.parent_id IS NULL
        AND ip.param_name = field.param_name
  );

-- events 数组元素字段。
INSERT INTO interface_param (
    interface_id, direction, parent_id, param_name, description, param_type,
    array_item_type, required, sort, example_value
)
SELECT ai.id, 'RESPONSE', events.id, field.param_name, field.description,
       field.param_type, field.array_item_type, field.required, field.sort, field.example_value
FROM api_interface ai
JOIN interface_param events
  ON events.interface_id = ai.id
 AND events.direction = 'RESPONSE'
 AND events.parent_id IS NULL
 AND events.param_name = 'events'
CROSS JOIN (VALUES
    ('year', '事件年份', 'integer', NULL::VARCHAR, true, 10, '1969'),
    ('month', '事件月份；上游未知时可能为 0', 'integer', NULL::VARCHAR, false, 20, '0'),
    ('day', '事件日期；上游未知时可能为 0', 'integer', NULL::VARCHAR, false, 30, '0'),
    ('title', '事件标题', 'string', NULL::VARCHAR, true, 40, '阿波罗11号宇航员首次登上月球表面。'),
    ('description', '事件描述', 'string', NULL::VARCHAR, true, 50, NULL),
    ('category', '事件分类', 'string', NULL::VARCHAR, true, 60, '事件'),
    ('tags', '事件标签', 'array', 'string'::VARCHAR, false, 70, NULL),
    ('importance', '重要性评分', 'integer', NULL::VARCHAR, true, 80, '10'),
    ('source', '数据来源', 'string', NULL::VARCHAR, true, 90, '历史数据')
) AS field(param_name, description, param_type, array_item_type, required, sort, example_value)
WHERE ai.interface_code = 'PROGRAMMER_HISTORY_TODAY'
  AND NOT EXISTS (
      SELECT 1
      FROM interface_param ip
      WHERE ip.interface_id = ai.id
        AND ip.direction = 'RESPONSE'
        AND ip.parent_id = events.id
        AND ip.param_name = field.param_name
  );
