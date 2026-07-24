-- 仅允许在尚未产生审批业务数据或新来源授权时回滚。

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM api_permission_application) THEN
        RAISE EXCEPTION '已存在接口权限申请，禁止回滚 V026';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM api_key_interface
        WHERE grant_source <> 'LEGACY_ADMIN'
           OR application_item_id IS NOT NULL
           OR status <> 'ACTIVE'
           OR expire_at IS NOT NULL
           OR revoked_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION '已存在审批、紧急、到期或撤销授权，禁止回滚 V026';
    END IF;
END $$;

DELETE FROM role_permission
WHERE permission_id IN (
    SELECT id
    FROM permission
    WHERE permission_code LIKE 'api-permission:%'
);

DELETE FROM permission
WHERE permission_code LIKE 'api-permission:%';

DROP TABLE api_permission_action;

ALTER TABLE api_permission_application_item
    DROP CONSTRAINT fk_api_perm_item_grant;

DROP INDEX uk_api_key_interface_application_item;
DROP INDEX uk_api_key_interface_key_interface;
DROP INDEX idx_api_key_interface_effective;

ALTER TABLE api_key_interface
    DROP CONSTRAINT fk_api_key_interface_application_item,
    DROP CONSTRAINT ck_api_key_interface_source,
    DROP CONSTRAINT ck_api_key_interface_status,
    DROP CONSTRAINT ck_api_key_interface_expiry,
    DROP CONSTRAINT ck_api_key_interface_version,
    DROP COLUMN grant_source,
    DROP COLUMN application_item_id,
    DROP COLUMN status,
    DROP COLUMN effective_at,
    DROP COLUMN expire_at,
    DROP COLUMN revoked_at,
    DROP COLUMN revoked_by,
    DROP COLUMN revoke_reason,
    DROP COLUMN updated_at,
    DROP COLUMN version;

DROP TABLE api_permission_application_item;
DROP TABLE api_permission_application;
DROP TABLE api_approval_process_config;
