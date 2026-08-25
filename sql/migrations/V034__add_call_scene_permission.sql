-- The scene-management route has always required call-scene:view, but the
-- bootstrap permission catalog never created that permission.
INSERT INTO permission (
    permission_code, permission_name, resource_type, resource_path,
    parent_id, sort_order, description, status, deleted
)
VALUES (
    'call-scene:view', '场景管理-查看', 'page', '/call-scene',
    0, 10, '查看场景管理', 'active', FALSE
)
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    resource_type = EXCLUDED.resource_type,
    resource_path = EXCLUDED.resource_path,
    parent_id = EXCLUDED.parent_id,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role_info.id, permission.id, CURRENT_TIMESTAMP
FROM role_info
JOIN permission ON permission.permission_code = 'call-scene:view'
WHERE LOWER(role_info.role_code) = 'admin'
  AND role_info.status = 'active'
  AND role_info.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;
