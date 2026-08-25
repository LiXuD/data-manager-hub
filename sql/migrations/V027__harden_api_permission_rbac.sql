-- 接口权限审批安全整改：
-- 1. 角色编码统一为小写并合并大小写重复角色；
-- 2. 默认候选组统一为小写；
-- 3. 新增平台安全管理权限和审批角色矩阵；
-- 4. 不改变既有非审批权限和 api_key_interface 授权事实。

DROP TABLE IF EXISTS v027_role_code_map;
CREATE TEMP TABLE v027_role_code_map AS
SELECT
    id AS old_role_id,
    MIN(id) OVER (PARTITION BY LOWER(role_code)) AS canonical_role_id,
    LOWER(role_code) AS canonical_role_code
FROM role_info;

INSERT INTO user_role (user_id, role_id, created_at)
SELECT ur.user_id, mapping.canonical_role_id, ur.created_at
FROM user_role ur
JOIN v027_role_code_map mapping ON mapping.old_role_id = ur.role_id
WHERE mapping.old_role_id <> mapping.canonical_role_id
ON CONFLICT (user_id, role_id) DO NOTHING;

DELETE FROM user_role relation
USING v027_role_code_map mapping
WHERE relation.role_id = mapping.old_role_id
  AND mapping.old_role_id <> mapping.canonical_role_id;

INSERT INTO role_permission (role_id, permission_id, created_by, created_at)
SELECT
    mapping.canonical_role_id,
    relation.permission_id,
    relation.created_by,
    relation.created_at
FROM role_permission relation
JOIN v027_role_code_map mapping ON mapping.old_role_id = relation.role_id
WHERE mapping.old_role_id <> mapping.canonical_role_id
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM role_permission relation
USING v027_role_code_map mapping
WHERE relation.role_id = mapping.old_role_id
  AND mapping.old_role_id <> mapping.canonical_role_id;

DELETE FROM role_info role
USING v027_role_code_map mapping
WHERE role.id = mapping.old_role_id
  AND mapping.old_role_id <> mapping.canonical_role_id;

UPDATE role_info role
SET role_code = mapping.canonical_role_code,
    updated_at = CURRENT_TIMESTAMP
FROM v027_role_code_map mapping
WHERE role.id = mapping.canonical_role_id
  AND role.role_code <> mapping.canonical_role_code;

ALTER TABLE role_info
    DROP CONSTRAINT IF EXISTS ck_role_info_code_lowercase;
ALTER TABLE role_info
    ADD CONSTRAINT ck_role_info_code_lowercase
        CHECK (role_code = LOWER(role_code));

UPDATE api_approval_process_config
SET approver_group = LOWER(TRIM(approver_group)),
    updated_at = CURRENT_TIMESTAMP
WHERE approver_group <> LOWER(TRIM(approver_group));

ALTER TABLE api_approval_process_config
    DROP CONSTRAINT IF EXISTS ck_api_approval_group_lowercase;
ALTER TABLE api_approval_process_config
    ADD CONSTRAINT ck_api_approval_group_lowercase
        CHECK (approver_group = LOWER(TRIM(approver_group))
            AND LENGTH(TRIM(approver_group)) > 0);

INSERT INTO permission (
    permission_code, permission_name, resource_type, resource_path,
    parent_id, sort_order, description, status, deleted
)
VALUES (
    'system:admin',
    '平台安全管理',
    'system',
    '/system/security',
    0,
    0,
    '跨租户 IAM 与全局角色权限目录管理；禁止通过角色名称硬编码判断',
    'active',
    FALSE
)
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    resource_type = EXCLUDED.resource_type,
    resource_path = EXCLUDED.resource_path,
    description = EXCLUDED.description,
    status = 'active',
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_info (
    role_code, role_name, description, status, created_at, updated_at, deleted
)
VALUES
    ('tenant_admin', '租户管理员', '负责本租户用户与业务审批管理', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('api_interface_approver', '接口权限审批员', '负责接口调用权限审批', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('data_security_approver', '数据安全审批员', '负责敏感数据接口审批', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('api_process_admin', '接口审批流程管理员', '负责接口审批流程版本和路由配置', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('platform_security_admin', '平台安全管理员', '负责撤销和紧急接口授权', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (role_code) DO NOTHING;

DELETE FROM role_permission relation
USING role_info role, permission permission
WHERE relation.role_id = role.id
  AND relation.permission_id = permission.id
  AND role.role_code IN (
      'admin',
      'user',
      'tenant_admin',
      'api_interface_approver',
      'data_security_approver',
      'api_process_admin',
      'platform_security_admin'
  )
  AND permission.permission_code LIKE 'api-permission:%';

DELETE FROM role_permission relation
USING permission permission
WHERE relation.permission_id = permission.id
  AND permission.permission_code = 'system:admin';

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM role_info role
JOIN permission ON permission.permission_code = 'system:admin'
WHERE role.role_code = 'admin'
  AND role.status = 'active'
  AND role.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM role_info role
JOIN permission ON permission.permission_code LIKE 'api-permission:%'
WHERE role.role_code = 'admin'
  AND role.status = 'active'
  AND role.deleted = FALSE
  AND permission.status = 'active'
  AND permission.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM role_info role
JOIN permission ON permission.permission_code IN (
    'api-permission:view',
    'api-permission:apply'
)
WHERE role.role_code = 'user'
  AND role.status = 'active'
  AND role.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM role_info role
JOIN permission ON permission.permission_code LIKE 'api-permission:%'
WHERE role.role_code = 'tenant_admin'
  AND permission.permission_code <> 'api-permission:emergency-grant'
  AND role.status = 'active'
  AND role.deleted = FALSE
  AND permission.status = 'active'
  AND permission.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM role_info role
JOIN permission ON permission.permission_code IN (
    'api-permission:view',
    'api-permission:approve',
    'api-permission:grant-view'
)
WHERE role.role_code IN ('api_interface_approver', 'data_security_approver')
  AND role.status = 'active'
  AND role.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM role_info role
JOIN permission ON permission.permission_code IN (
    'api-permission:process-view',
    'api-permission:process-manage'
)
WHERE role.role_code = 'api_process_admin'
  AND role.status = 'active'
  AND role.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM role_info role
JOIN permission ON permission.permission_code IN (
    'api-permission:grant-view',
    'api-permission:revoke',
    'api-permission:emergency-grant',
    'api-permission:process-view'
)
WHERE role.role_code = 'platform_security_admin'
  AND role.status = 'active'
  AND role.deleted = FALSE
ON CONFLICT (role_id, permission_id) DO NOTHING;

DROP TABLE v027_role_code_map;
