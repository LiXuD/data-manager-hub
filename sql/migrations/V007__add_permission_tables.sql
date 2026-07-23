-- ==========================================
-- 权限管理相关表
-- ==========================================

-- 1. 权限表（permission）
CREATE TABLE IF NOT EXISTS permission (
    id BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    permission_name VARCHAR(100) NOT NULL,
    resource_type VARCHAR(20) NOT NULL, -- page, button, api
    resource_path VARCHAR(200),
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    description TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE
);

COMMENT ON TABLE permission IS '权限表';
COMMENT ON COLUMN permission.permission_code IS '权限编码，如 user:view, billing:edit';
COMMENT ON COLUMN permission.resource_type IS '资源类型：page(页面), button(按钮), api(接口)';

-- 2. 角色权限关联表（role_permission）
CREATE TABLE IF NOT EXISTS role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(role_id, permission_id)
);

COMMENT ON TABLE role_permission IS '角色权限关联表';

-- 3. 用户调用方关联表（user_caller）
CREATE TABLE IF NOT EXISTS user_caller (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    caller_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, caller_id)
);

COMMENT ON TABLE user_caller IS '用户调用方关联表';

-- 4. API Key 接口授权表（api_key_interface）
CREATE TABLE IF NOT EXISTS api_key_interface (
    id BIGSERIAL PRIMARY KEY,
    api_key_id BIGINT NOT NULL,
    interface_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(api_key_id, interface_id)
);

COMMENT ON TABLE api_key_interface IS 'API Key 接口授权表';

-- ==========================================
-- 初始化数据
-- ==========================================

-- 初始化权限数据
INSERT INTO permission (permission_code, permission_name, resource_type, resource_path, parent_id, sort_order, description, status)
VALUES
-- 系统管理模块
('system:view', '系统管理-查看', 'page', '/system', 0, 1, '查看系统管理菜单', 'active'),
('tenant:view', '租户管理-查看', 'page', '/tenant', 1, 1, '查看租户管理', 'active'),
('tenant:add', '租户管理-新增', 'button', '/tenant/add', 1, 2, '新增租户', 'active'),
('tenant:edit', '租户管理-编辑', 'button', '/tenant/edit', 1, 3, '编辑租户', 'active'),
('tenant:delete', '租户管理-删除', 'button', '/tenant/delete', 1, 4, '删除租户', 'active'),
('user:view', '用户管理-查看', 'page', '/user', 1, 5, '查看用户管理', 'active'),
('user:add', '用户管理-新增', 'button', '/user/add', 1, 6, '新增用户', 'active'),
('user:edit', '用户管理-编辑', 'button', '/user/edit', 1, 7, '编辑用户', 'active'),
('user:delete', '用户管理-删除', 'button', '/user/delete', 1, 8, '删除用户', 'active'),
('role:view', '角色管理-查看', 'page', '/role', 1, 9, '查看角色管理', 'active'),
('role:add', '角色管理-新增', 'button', '/role/add', 1, 10, '新增角色', 'active'),
('role:edit', '角色管理-编辑', 'button', '/role/edit', 1, 11, '编辑角色', 'active'),
('role:delete', '角色管理-删除', 'button', '/role/delete', 1, 12, '删除角色', 'active'),
-- 业务管理模块
('business:view', '业务管理-查看', 'page', '/business', 0, 2, '查看业务管理菜单', 'active'),
('vendor:view', '厂商管理-查看', 'page', '/vendor', 2, 1, '查看厂商管理', 'active'),
('vendor:add', '厂商管理-新增', 'button', '/vendor/add', 2, 2, '新增厂商', 'active'),
('vendor:edit', '厂商管理-编辑', 'button', '/vendor/edit', 2, 3, '编辑厂商', 'active'),
('vendor:delete', '厂商管理-删除', 'button', '/vendor/delete', 2, 4, '删除厂商', 'active'),
('caller:view', '调用方管理-查看', 'page', '/caller', 2, 5, '查看调用方管理', 'active'),
('caller:add', '调用方管理-新增', 'button', '/caller/add', 2, 6, '新增调用方', 'active'),
('caller:edit', '调用方管理-编辑', 'button', '/caller/edit', 2, 7, '编辑调用方', 'active'),
('caller:delete', '调用方管理-删除', 'button', '/caller/delete', 2, 8, '删除调用方', 'active'),
('datatype:view', '数据类型-查看', 'page', '/datatype', 2, 9, '查看数据类型', 'active'),
('datatype:add', '数据类型-新增', 'button', '/datatype/add', 2, 10, '新增数据类型', 'active'),
('datatype:edit', '数据类型-编辑', 'button', '/datatype/edit', 2, 11, '编辑数据类型', 'active'),
('datatype:delete', '数据类型-删除', 'button', '/datatype/delete', 2, 12, '删除数据类型', 'active'),
('interface:view', '接口管理-查看', 'page', '/interface', 2, 13, '查看接口管理', 'active'),
('interface:add', '接口管理-新增', 'button', '/interface/add', 2, 14, '新增接口', 'active'),
('interface:edit', '接口管理-编辑', 'button', '/interface/edit', 2, 15, '编辑接口', 'active'),
('interface:delete', '接口管理-删除', 'button', '/interface/delete', 2, 16, '删除接口', 'active'),
-- 核心功能模块
('call:view', '调用记录-查看', 'page', '/call', 0, 3, '查看调用记录', 'active'),
('billing:view', '计费管理-查看', 'page', '/billing', 0, 4, '查看计费管理', 'active'),
('monitor:view', '监控告警-查看', 'page', '/monitor', 0, 5, '查看监控告警', 'active'),
('config:view', '配置中心-查看', 'page', '/config', 0, 6, '查看配置中心', 'active'),
('config:edit', '配置中心-编辑', 'button', '/config/edit', 6, 1, '编辑配置', 'active'),
('graylog:view', '灰度发布-查看', 'page', '/graylog', 0, 7, '查看灰度发布', 'active'),
('audit:view', '操作日志-查看', 'page', '/audit', 0, 8, '查看操作日志', 'active'),
('dashboard:view', '数据概览-查看', 'page', '/dashboard', 0, 9, '查看数据概览', 'active')
ON CONFLICT (permission_code) DO NOTHING;

-- 初始化管理员角色（如果不存在）
INSERT INTO role_info (role_code, role_name, description, status, created_at, updated_at, deleted)
SELECT 'admin', '管理员', '系统管理员，拥有所有权限', 'active', NOW(), NOW(), FALSE
WHERE NOT EXISTS (SELECT 1 FROM role_info WHERE role_code = 'admin');

-- 初始化普通用户角色
INSERT INTO role_info (role_code, role_name, description, status, created_at, updated_at, deleted)
SELECT 'user', '普通用户', '普通业务用户', 'active', NOW(), NOW(), FALSE
WHERE NOT EXISTS (SELECT 1 FROM role_info WHERE role_code = 'user');

-- 为管理员角色分配所有权限
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role_info r, permission p
WHERE LOWER(r.role_code) = 'admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 更新 admin 用户的 tenantId（如果未设置）
UPDATE user_info
SET tenant_id = 1
WHERE username = 'admin'
  AND tenant_id IS NULL;

-- 更新现有的 caller_info 的 tenantId（如果未设置）
UPDATE caller_info
SET tenant_id = 1
WHERE tenant_id IS NULL;
