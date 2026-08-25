-- Guard for the one-time onboarding of a database that was initialized before
-- Liquibase was introduced. Any missing object aborts changelogSync.
DO $$
DECLARE
    required_table TEXT;
BEGIN
    FOREACH required_table IN ARRAY ARRAY[
        'tenant_info', 'vendor_info', 'data_type', 'vendor_config',
        'api_interface', 'interface_param', 'permission', 'role_permission',
        'call_record', 'billing_template', 'billing_plan', 'billing_event',
        'billing_usage_balance'
    ] LOOP
        IF to_regclass('public.' || required_table) IS NULL THEN
            RAISE EXCEPTION '现有数据库不满足 Liquibase 基线：缺少表 %', required_table;
        END IF;
    END LOOP;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'interface_param'
          AND column_name = 'array_item_type'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'api_key'
          AND column_name = 'rate_limit_enabled'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'call_record'
          AND column_name = 'response_contract_valid'
    ) THEN
        RAISE EXCEPTION '现有数据库不满足 Liquibase 基线：最新字段不完整';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'interface_param'
          AND column_name = 'validation_rule'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'vendor_config'
          AND column_name IN ('sign_type', 'encrypt_type')
    ) THEN
        RAISE EXCEPTION '现有数据库不满足 Liquibase 基线：仍存在已废弃兼容字段';
    END IF;

    IF (SELECT count(*) FROM permission
        WHERE permission_code IN (
            'billing:view', 'billing:manage', 'billing:reverse',
            'billing:reconcile', 'billing:view-all'
        )) <> 5 THEN
        RAISE EXCEPTION '现有数据库不满足 Liquibase 基线：计费权限不完整';
    END IF;
END $$;
