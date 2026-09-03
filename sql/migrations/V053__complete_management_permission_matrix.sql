-- Complete the authenticated management-route permission vocabulary.
-- This migration is forward-only: previously executed permission facts are not
-- deleted or rewritten. Built-in admin receives the new explicit capabilities;
-- custom roles require an intentional administrator assignment.
INSERT INTO permission (
    permission_code, permission_name, resource_type, resource_path,
    parent_id, sort_order, description, status, deleted
)
VALUES
    ('permission:view', '权限目录-查看', 'page', '/permission', 0, 90, '查看权限目录', 'active', FALSE),
    ('permission:add', '权限目录-新增', 'button', '/permission/add', 0, 91, '新增权限', 'active', FALSE),
    ('permission:edit', '权限目录-编辑', 'button', '/permission/edit', 0, 92, '编辑权限', 'active', FALSE),
    ('permission:delete', '权限目录-删除', 'button', '/permission/delete', 0, 93, '删除权限', 'active', FALSE),
    ('config:add', '配置中心-新增', 'button', '/config/add',
        (SELECT id FROM permission WHERE permission_code = 'config:view'),
        94, '新增配置', 'active', FALSE),
    ('config:delete', '配置中心-删除', 'button', '/config/delete',
        (SELECT id FROM permission WHERE permission_code = 'config:view'),
        95, '删除配置', 'active', FALSE),
    ('apikey:view', 'API Key-查看', 'page', '/caller/apikey', 2, 96, '查看当前租户 API Key', 'active', FALSE),
    ('apikey:add', 'API Key-新增', 'button', '/caller/apikey/add', 2, 97, '新增 API Key', 'active', FALSE),
    ('apikey:edit', 'API Key-编辑', 'button', '/caller/apikey/edit', 2, 98, '编辑 API Key', 'active', FALSE),
    ('apikey:delete', 'API Key-删除', 'button', '/caller/apikey/delete', 2, 99, '删除 API Key', 'active', FALSE),
    ('call:export', '调用记录-导出', 'button', '/call/export', 0, 100, '导出当前数据范围内的调用记录', 'active', FALSE),
    ('call-scene:add', '调用场景-新增', 'button', '/call-scene/add', 0, 101, '新增调用场景', 'active', FALSE),
    ('call-scene:edit', '调用场景-编辑', 'button', '/call-scene/edit', 0, 102, '编辑调用场景名称和描述', 'active', FALSE),
    ('call-scene:disable', '调用场景-停用', 'button', '/call-scene/disable', 0, 103, '停用调用场景', 'active', FALSE),
    ('graylog:add', '灰度发布-新增', 'button', '/graylog/add', 0, 105, '新增灰度规则', 'active', FALSE),
    ('graylog:edit', '灰度发布-编辑', 'button', '/graylog/edit', 0, 106, '编辑灰度规则', 'active', FALSE),
    ('graylog:delete', '灰度发布-删除', 'button', '/graylog/delete', 0, 107, '删除灰度规则', 'active', FALSE),
    ('monitor:manage', '监控告警-管理', 'button', '/monitor/manage', 0, 108, '维护告警规则并处理告警记录', 'active', FALSE),
    ('quality:view', '数据质量-查看', 'page', '/quality', 0, 109, '查看质量规则和历史', 'active', FALSE),
    ('quality:manage', '数据质量-管理', 'button', '/quality/manage', 0, 110, '维护质量规则并执行检查', 'active', FALSE),
    ('trace:view', '数据血缘-查看', 'page', '/trace/lineage', 0, 111, '查看数据血缘', 'active', FALSE),
    ('trace:manage', '数据血缘-管理', 'button', '/trace/lineage/manage', 0, 112, '写入和维护数据血缘', 'active', FALSE),
    ('security:manage', '安全加密-管理', 'system', '/security/encryption', 0, 113, '执行受控加密、解密和密钥轮换', 'active', FALSE)
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    resource_type = EXCLUDED.resource_type,
    resource_path = EXCLUDED.resource_path,
    parent_id = EXCLUDED.parent_id,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    status = 'active',
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role_info.id, permission.id, CURRENT_TIMESTAMP
FROM role_info
JOIN permission ON permission.permission_code IN (
    'permission:view', 'permission:add', 'permission:edit', 'permission:delete',
    'config:add', 'config:delete',
    'apikey:view', 'apikey:add', 'apikey:edit', 'apikey:delete',
    'call:export',
    'call-scene:add', 'call-scene:edit', 'call-scene:disable',
    'graylog:add', 'graylog:edit', 'graylog:delete', 'monitor:manage',
    'quality:view', 'quality:manage', 'trace:view', 'trace:manage', 'security:manage'
)
WHERE LOWER(role_info.role_code) = 'admin'
  AND role_info.status = 'active'
  AND role_info.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;
