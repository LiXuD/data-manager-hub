-- 仅在尚未产生新的缓存审批事实时允许回滚。

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM api_permission_application_item
        WHERE requested_cache_enabled
           OR requested_cache_days IS NOT NULL
           OR approved_cache_enabled
           OR approved_cache_days IS NOT NULL
    ) THEN
        RAISE EXCEPTION '已存在缓存策略申请或审批数据，禁止回滚 V028';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM api_key_interface
        WHERE cache_enabled IS DISTINCT FROM TRUE
           OR approved_cache_days IS DISTINCT FROM 365
    ) THEN
        RAISE EXCEPTION '已存在非兼容默认值的缓存授权策略，禁止回滚 V028';
    END IF;
END $$;

DROP INDEX idx_api_key_interface_cache_policy;

ALTER TABLE api_key_interface
    DROP CONSTRAINT ck_api_key_interface_cache_policy,
    DROP COLUMN cache_enabled,
    DROP COLUMN approved_cache_days;

ALTER TABLE api_permission_application_item
    DROP CONSTRAINT ck_api_perm_item_requested_cache,
    DROP CONSTRAINT ck_api_perm_item_approved_cache,
    DROP COLUMN requested_cache_enabled,
    DROP COLUMN requested_cache_days,
    DROP COLUMN approved_cache_enabled,
    DROP COLUMN approved_cache_days;

ALTER TABLE caller_product
    ALTER COLUMN cache_scope SET DEFAULT 'GLOBAL';
