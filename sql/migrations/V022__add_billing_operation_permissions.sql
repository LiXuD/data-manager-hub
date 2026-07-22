-- 计费操作级权限：方案维护(增删改/发布/计提) 与 事件冲正(高敏感)。
-- 补齐 BillingPlanController 的细粒度授权，计费此前仅有页面级 billing:view。
INSERT INTO permission (permission_code, permission_name, resource_type, resource_path, parent_id, sort_order, description, status)
VALUES
    ('billing:manage',  '计费管理-方案维护', 'button', '/billing/plan',          0, 41, '创建/编辑/删除/发布计费方案与周期计提', 'active'),
    ('billing:reverse', '计费管理-事件冲正', 'button', '/billing/event/reverse', 0, 42, '对已入账计费事件执行冲正',              'active')
ON CONFLICT (permission_code) DO NOTHING;

-- 授予管理员角色（与 V007 授权范式一致）。
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role_info r, permission p
WHERE LOWER(r.role_code) = 'admin'
  AND p.permission_code IN ('billing:manage', 'billing:reverse')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
