-- 将接口结果缓存能力纳入接口权限申请、审批和授权事实。

ALTER TABLE api_permission_application_item
    ADD COLUMN requested_cache_enabled BOOLEAN,
    ADD COLUMN requested_cache_days INTEGER,
    ADD COLUMN approved_cache_enabled BOOLEAN,
    ADD COLUMN approved_cache_days INTEGER;

UPDATE api_permission_application_item
SET requested_cache_enabled = FALSE,
    approved_cache_enabled = FALSE;

ALTER TABLE api_permission_application_item
    ALTER COLUMN requested_cache_enabled SET DEFAULT FALSE,
    ALTER COLUMN requested_cache_enabled SET NOT NULL,
    ALTER COLUMN approved_cache_enabled SET DEFAULT FALSE,
    ALTER COLUMN approved_cache_enabled SET NOT NULL,
    ADD CONSTRAINT ck_api_perm_item_requested_cache
        CHECK (
            (requested_cache_enabled
                AND requested_cache_days BETWEEN 1 AND 365)
            OR
            (NOT requested_cache_enabled
                AND requested_cache_days IS NULL)
        ),
    ADD CONSTRAINT ck_api_perm_item_approved_cache
        CHECK (
            (approved_cache_enabled
                AND requested_cache_enabled
                AND approved_cache_days BETWEEN 1 AND requested_cache_days)
            OR
            (NOT approved_cache_enabled
                AND approved_cache_days IS NULL)
        );

ALTER TABLE api_key_interface
    ADD COLUMN cache_enabled BOOLEAN,
    ADD COLUMN approved_cache_days INTEGER;

-- 旧授权在上线前没有服务端缓存天数上限。
-- 回填为本版本支持的 365 天上限，避免迁移后收窄现有调用能力。
UPDATE api_key_interface
SET cache_enabled = TRUE,
    approved_cache_days = 365;

ALTER TABLE api_key_interface
    ALTER COLUMN cache_enabled SET DEFAULT FALSE,
    ALTER COLUMN cache_enabled SET NOT NULL,
    ADD CONSTRAINT ck_api_key_interface_cache_policy
        CHECK (
            (cache_enabled AND approved_cache_days BETWEEN 1 AND 365)
            OR
            (NOT cache_enabled AND approved_cache_days IS NULL)
        );

CREATE INDEX idx_api_key_interface_cache_policy
    ON api_key_interface(api_key_id, interface_id, status, cache_enabled);

-- 新建产品默认只允许调用方内复用；已经显式配置为 GLOBAL 的历史产品保持不变。
ALTER TABLE caller_product
    ALTER COLUMN cache_scope SET DEFAULT 'CALLER';
