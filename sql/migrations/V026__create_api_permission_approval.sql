-- API 接口权限申请、审批轨迹、流程路由和授权事实扩展。

DO $$
BEGIN
    IF EXISTS (
        SELECT api_key_id, interface_id
        FROM api_key_interface
        GROUP BY api_key_id, interface_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'api_key_interface 存在重复授权，请先清理后再执行 V026';
    END IF;
END $$;

CREATE TABLE api_permission_application (
    id BIGSERIAL PRIMARY KEY,
    application_no VARCHAR(40) NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    tenant_id BIGINT NOT NULL,
    caller_id BIGINT NOT NULL,
    caller_code_snapshot VARCHAR(50) NOT NULL,
    caller_name_snapshot VARCHAR(100) NOT NULL,
    api_key_id BIGINT NOT NULL,
    api_key_name_snapshot VARCHAR(100),
    applicant_user_id BIGINT NOT NULL,
    applicant_name_snapshot VARCHAR(100) NOT NULL,
    business_purpose TEXT NOT NULL,
    business_scene VARCHAR(200) NOT NULL,
    expected_daily_calls BIGINT NOT NULL,
    ticket_no VARCHAR(100),
    requested_expire_at TIMESTAMP,
    approved_expire_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    engine_type VARCHAR(20) NOT NULL DEFAULT 'FLOWABLE',
    engine_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    process_definition_key VARCHAR(100),
    process_definition_version INTEGER,
    process_instance_id VARCHAR(100),
    current_task_id VARCHAR(100),
    current_task_key VARCHAR(100),
    current_task_name VARCHAR(200),
    current_task_created_at TIMESTAMP,
    submitted_at TIMESTAMP,
    decided_by BIGINT,
    decided_by_name_snapshot VARCHAR(100),
    decided_at TIMESTAMP,
    decision_comment TEXT,
    idempotency_key VARCHAR(64),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_api_perm_application_no UNIQUE (application_no),
    CONSTRAINT fk_api_perm_application_caller FOREIGN KEY (caller_id) REFERENCES caller_info(id),
    CONSTRAINT fk_api_perm_application_api_key FOREIGN KEY (api_key_id) REFERENCES api_key(id),
    CONSTRAINT ck_api_perm_application_request_type
        CHECK (request_type IN ('OPEN', 'RENEW')),
    CONSTRAINT ck_api_perm_application_status
        CHECK (status IN ('DRAFT', 'IN_REVIEW', 'PROVISIONING', 'EFFECTIVE',
                          'REJECTED', 'CANCELED', 'ENGINE_ERROR', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_api_perm_application_engine_type
        CHECK (engine_type IN ('FLOWABLE', 'CAMUNDA8')),
    CONSTRAINT ck_api_perm_application_engine_status
        CHECK (engine_status IN ('NOT_STARTED', 'RUNNING', 'COMPLETED', 'TERMINATED', 'ERROR')),
    CONSTRAINT ck_api_perm_application_calls CHECK (expected_daily_calls > 0),
    CONSTRAINT ck_api_perm_application_version CHECK (version >= 0),
    CONSTRAINT ck_api_perm_application_expiry
        CHECK (approved_expire_at IS NULL
               OR requested_expire_at IS NULL
               OR approved_expire_at <= requested_expire_at)
);

CREATE UNIQUE INDEX uk_api_perm_application_idempotency
    ON api_permission_application(applicant_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uk_api_perm_application_process
    ON api_permission_application(engine_type, process_instance_id)
    WHERE process_instance_id IS NOT NULL;
CREATE INDEX idx_api_perm_application_tenant_status
    ON api_permission_application(tenant_id, status, submitted_at DESC);
CREATE INDEX idx_api_perm_application_applicant
    ON api_permission_application(applicant_user_id, created_at DESC);
CREATE INDEX idx_api_perm_application_caller_key
    ON api_permission_application(caller_id, api_key_id, status);
CREATE INDEX idx_api_perm_application_engine_task
    ON api_permission_application(engine_status, current_task_key, current_task_created_at);

CREATE TABLE api_permission_application_item (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL,
    api_key_id BIGINT NOT NULL,
    interface_id BIGINT NOT NULL,
    interface_code_snapshot VARCHAR(100) NOT NULL,
    interface_name_snapshot VARCHAR(200) NOT NULL,
    interface_status_snapshot VARCHAR(20) NOT NULL,
    item_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    grant_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_perm_item_application
        FOREIGN KEY (application_id) REFERENCES api_permission_application(id),
    CONSTRAINT fk_api_perm_item_api_key
        FOREIGN KEY (api_key_id) REFERENCES api_key(id),
    CONSTRAINT uk_api_perm_item_application_interface UNIQUE (application_id, interface_id),
    CONSTRAINT ck_api_perm_item_status
        CHECK (item_status IN ('DRAFT', 'IN_REVIEW', 'PROVISIONING', 'EFFECTIVE',
                               'REJECTED', 'CANCELED', 'ENGINE_ERROR', 'EXPIRED', 'REVOKED'))
);

CREATE INDEX idx_api_perm_item_key_interface_status
    ON api_permission_application_item(api_key_id, interface_id, item_status);
CREATE UNIQUE INDEX uk_api_perm_item_pending
    ON api_permission_application_item(api_key_id, interface_id)
    WHERE item_status IN ('IN_REVIEW', 'PROVISIONING');

CREATE TABLE api_permission_action (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT,
    action VARCHAR(30) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id BIGINT,
    actor_name_snapshot VARCHAR(100),
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    comment TEXT,
    engine_type VARCHAR(20),
    process_instance_id VARCHAR(100),
    task_id VARCHAR(100),
    task_definition_key VARCHAR(100),
    task_name VARCHAR(200),
    task_assignee VARCHAR(100),
    process_definition_version INTEGER,
    trace_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_perm_action_application
        FOREIGN KEY (application_id) REFERENCES api_permission_application(id),
    CONSTRAINT ck_api_perm_action_type
        CHECK (action IN ('CREATE', 'SUBMIT', 'APPROVE', 'REJECT', 'CANCEL',
                          'EXPIRE', 'REVOKE', 'EMERGENCY_GRANT', 'GRANT')),
    CONSTRAINT ck_api_perm_action_actor CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT ck_api_perm_action_engine
        CHECK (engine_type IS NULL OR engine_type IN ('FLOWABLE', 'CAMUNDA8'))
);

CREATE INDEX idx_api_perm_action_application_created
    ON api_permission_action(application_id, created_at);
CREATE INDEX idx_api_perm_action_process_task
    ON api_permission_action(process_instance_id, task_id);

CREATE TABLE api_approval_process_config (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    business_type VARCHAR(50) NOT NULL,
    risk_level VARCHAR(20) NOT NULL DEFAULT '*',
    engine_type VARCHAR(20) NOT NULL,
    process_definition_key VARCHAR(100) NOT NULL,
    approver_group VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_api_approval_process_route
        UNIQUE (tenant_id, business_type, risk_level, priority),
    CONSTRAINT ck_api_approval_process_business
        CHECK (business_type IN ('API_PERMISSION_OPEN', 'API_PERMISSION_RENEW')),
    CONSTRAINT ck_api_approval_process_risk
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', '*')),
    CONSTRAINT ck_api_approval_process_engine
        CHECK (engine_type IN ('FLOWABLE', 'CAMUNDA8')),
    CONSTRAINT ck_api_approval_process_priority CHECK (priority >= 0),
    CONSTRAINT ck_api_approval_process_version CHECK (version >= 0)
);

CREATE INDEX idx_api_approval_process_match
    ON api_approval_process_config(tenant_id, business_type, risk_level, enabled, priority DESC);

ALTER TABLE api_key_interface
    ADD COLUMN grant_source VARCHAR(30),
    ADD COLUMN application_item_id BIGINT,
    ADD COLUMN status VARCHAR(20),
    ADD COLUMN effective_at TIMESTAMP,
    ADD COLUMN expire_at TIMESTAMP,
    ADD COLUMN revoked_at TIMESTAMP,
    ADD COLUMN revoked_by BIGINT,
    ADD COLUMN revoke_reason TEXT,
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN version INTEGER;

UPDATE api_key_interface
SET grant_source = 'LEGACY_ADMIN',
    status = 'ACTIVE',
    effective_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    version = 0;

ALTER TABLE api_key_interface
    ALTER COLUMN grant_source SET DEFAULT 'LEGACY_ADMIN',
    ALTER COLUMN grant_source SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN effective_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN effective_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL,
    ADD CONSTRAINT fk_api_key_interface_application_item
        FOREIGN KEY (application_item_id) REFERENCES api_permission_application_item(id),
    ADD CONSTRAINT ck_api_key_interface_source
        CHECK (grant_source IN ('LEGACY_ADMIN', 'APPROVAL', 'EMERGENCY_ADMIN')),
    ADD CONSTRAINT ck_api_key_interface_status
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    ADD CONSTRAINT ck_api_key_interface_expiry
        CHECK (expire_at IS NULL OR expire_at > effective_at),
    ADD CONSTRAINT ck_api_key_interface_version CHECK (version >= 0);

CREATE UNIQUE INDEX uk_api_key_interface_application_item
    ON api_key_interface(application_item_id)
    WHERE application_item_id IS NOT NULL;
CREATE UNIQUE INDEX uk_api_key_interface_key_interface
    ON api_key_interface(api_key_id, interface_id);
CREATE INDEX idx_api_key_interface_effective
    ON api_key_interface(api_key_id, interface_id, status, effective_at, expire_at);

ALTER TABLE api_permission_application_item
    ADD CONSTRAINT fk_api_perm_item_grant
        FOREIGN KEY (grant_id) REFERENCES api_key_interface(id);

INSERT INTO api_approval_process_config (
    tenant_id, business_type, risk_level, engine_type,
    process_definition_key, approver_group, enabled, priority
)
VALUES
    (0, 'API_PERMISSION_OPEN', '*', 'FLOWABLE', 'apiPermissionApproval', 'admin', TRUE, 0),
    (0, 'API_PERMISSION_RENEW', '*', 'FLOWABLE', 'apiPermissionApproval', 'admin', TRUE, 0)
ON CONFLICT (tenant_id, business_type, risk_level, priority) DO NOTHING;

INSERT INTO permission (
    permission_code, permission_name, resource_type, resource_path,
    parent_id, sort_order, description, status
)
VALUES
    ('api-permission:view', '接口权限申请-查看', 'page', '/api-permission', 0, 51, '查看本人可见的接口权限申请', 'active'),
    ('api-permission:apply', '接口权限申请-提交', 'button', '/api-permission/applications', 0, 52, '创建、编辑、提交和取消本人申请', 'active'),
    ('api-permission:approve', '接口权限申请-审批', 'button', '/api-permission/tasks', 0, 53, '认领并完成本租户审批任务', 'active'),
    ('api-permission:grant-view', '接口权限授权-查看', 'page', '/api-permission/grants', 0, 54, '查询本租户接口授权', 'active'),
    ('api-permission:revoke', '接口权限授权-撤销', 'button', '/api-permission/grants/revoke', 0, 55, '撤销已生效接口授权', 'active'),
    ('api-permission:emergency-grant', '接口权限授权-紧急开通', 'button', '/api-permission/emergency-grants', 0, 56, '按截止时间紧急增量授权', 'active'),
    ('api-permission:process-view', '接口权限流程-查看', 'page', '/api-permission/process', 0, 57, '查看流程版本、实例和节点诊断信息', 'active'),
    ('api-permission:process-manage', '接口权限流程-管理', 'button', '/api-permission/process/config', 0, 58, '启停接口权限流程路由配置', 'active')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM role_info role
JOIN permission ON permission.permission_code IN (
    'api-permission:view',
    'api-permission:apply',
    'api-permission:approve',
    'api-permission:grant-view',
    'api-permission:revoke',
    'api-permission:emergency-grant',
    'api-permission:process-view',
    'api-permission:process-manage'
)
WHERE LOWER(role.role_code) = 'admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM role_info role
JOIN permission ON permission.permission_code IN (
    'api-permission:view',
    'api-permission:apply'
)
WHERE LOWER(role.role_code) = 'user'
ON CONFLICT (role_id, permission_id) DO NOTHING;

COMMENT ON TABLE api_permission_application IS 'API 接口权限申请主表';
COMMENT ON TABLE api_permission_application_item IS 'API 接口权限申请项';
COMMENT ON TABLE api_permission_action IS 'API 接口权限不可变审批轨迹';
COMMENT ON TABLE api_approval_process_config IS '审批业务到流程定义的路由配置';
COMMENT ON COLUMN api_key_interface.grant_source IS '授权来源：历史管理、审批或紧急授权';
