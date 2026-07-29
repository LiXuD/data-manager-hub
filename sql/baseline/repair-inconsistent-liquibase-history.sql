-- Repair only changesets that were recorded by changelogSync while none of
-- their physical objects existed. Partial executions are rejected because
-- replaying their historical SQL would not be safe.

DO $$
DECLARE
    workflow_table_count INTEGER;
    v026_table_count INTEGER;
    v026_column_count INTEGER;
    v028_column_count INTEGER;
    v029_seed_count INTEGER;
BEGIN
    IF EXISTS (
        SELECT 1 FROM databasechangelog
        WHERE id = 'flowable-process-schema-7.1.0' AND author = 'flowable'
    ) AND to_regclass('workflow.act_ge_property') IS NULL THEN
        SELECT count(*) INTO workflow_table_count
        FROM information_schema.tables
        WHERE table_schema = 'workflow';
        IF workflow_table_count <> 0 THEN
            RAISE EXCEPTION 'Flowable 迁移处于部分执行状态（workflow tables=%），拒绝自动改写历史',
                workflow_table_count;
        END IF;
        DELETE FROM databasechangelog
        WHERE id = 'flowable-process-schema-7.1.0' AND author = 'flowable';
    END IF;

    IF EXISTS (
        SELECT 1 FROM databasechangelog
        WHERE id = 'api-permission-approval-2026-07-24' AND author = 'data-platform'
    ) AND NOT (
        to_regclass('public.api_permission_application') IS NOT NULL
        AND to_regclass('public.api_permission_application_item') IS NOT NULL
        AND to_regclass('public.api_permission_action') IS NOT NULL
        AND to_regclass('public.api_approval_process_config') IS NOT NULL
        AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'api_key_interface'
              AND column_name = 'grant_source'
        )
    ) THEN
        SELECT count(*) INTO v026_table_count
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name IN (
              'api_permission_application',
              'api_permission_application_item',
              'api_permission_action',
              'api_approval_process_config'
          );
        SELECT count(*) INTO v026_column_count
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'api_key_interface'
          AND column_name IN (
              'grant_source', 'application_item_id', 'status', 'effective_at',
              'expire_at', 'revoked_at', 'revoked_by', 'revoke_reason',
              'updated_at', 'version'
          );
        IF v026_table_count <> 0 OR v026_column_count <> 0 THEN
            RAISE EXCEPTION
                'V026 迁移处于部分执行状态（tables=%, columns=%），拒绝自动改写历史',
                v026_table_count, v026_column_count;
        END IF;
        DELETE FROM databasechangelog
        WHERE author = 'data-platform'
          AND id IN (
              'api-permission-approval-2026-07-24',
              'api-permission-rbac-hardening-2026-07-24',
              'api-permission-cache-policy-2026-07-24'
          );
    END IF;

    IF EXISTS (
        SELECT 1 FROM databasechangelog
        WHERE id = 'api-permission-rbac-hardening-2026-07-24'
          AND author = 'data-platform'
    ) AND NOT (
        EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'ck_role_info_code_lowercase'
              AND conrelid = 'public.role_info'::regclass
        )
        AND EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'ck_api_approval_group_lowercase'
              AND conrelid = to_regclass('public.api_approval_process_config')
        )
        AND EXISTS (
            SELECT 1 FROM permission
            WHERE permission_code = 'system:admin' AND deleted = FALSE
        )
    ) THEN
        DELETE FROM databasechangelog
        WHERE id = 'api-permission-rbac-hardening-2026-07-24'
          AND author = 'data-platform';
    END IF;

    IF EXISTS (
        SELECT 1 FROM databasechangelog
        WHERE id = 'api-permission-cache-policy-2026-07-24'
          AND author = 'data-platform'
    ) AND NOT (
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'api_permission_application_item'
              AND column_name = 'requested_cache_enabled'
        )
        AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'api_key_interface'
              AND column_name = 'cache_enabled'
        )
    ) THEN
        SELECT count(*) INTO v028_column_count
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND (
              (table_name = 'api_permission_application_item'
               AND column_name IN (
                   'requested_cache_enabled', 'requested_cache_days',
                   'approved_cache_enabled', 'approved_cache_days'
               ))
              OR
              (table_name = 'api_key_interface'
               AND column_name IN ('cache_enabled', 'approved_cache_days'))
          );
        IF v028_column_count <> 0 THEN
            RAISE EXCEPTION
                'V028 迁移处于部分执行状态（columns=%），拒绝自动改写历史',
                v028_column_count;
        END IF;
        DELETE FROM databasechangelog
        WHERE id = 'api-permission-cache-policy-2026-07-24'
          AND author = 'data-platform';
    END IF;

    IF EXISTS (
        SELECT 1 FROM databasechangelog
        WHERE id = 'uapi-programmer-history-by-date-2026-07-24'
          AND author = 'data-platform'
    ) THEN
        SELECT
            (CASE WHEN EXISTS (
                SELECT 1 FROM data_type
                WHERE data_type_code = 'programmer_history_by_date'
            ) THEN 1 ELSE 0 END)
          + (CASE WHEN EXISTS (
                SELECT 1 FROM api_interface
                WHERE interface_code = 'PROGRAMMER_HISTORY_BY_DATE'
            ) THEN 1 ELSE 0 END)
          + (CASE WHEN EXISTS (
                SELECT 1 FROM billing_plan
                WHERE plan_code = 'UAPI-PROGRAMMER-HISTORY-BY-DATE'
            ) THEN 1 ELSE 0 END)
        INTO v029_seed_count;
        IF v029_seed_count = 0 THEN
            DELETE FROM databasechangelog
            WHERE id = 'uapi-programmer-history-by-date-2026-07-24'
              AND author = 'data-platform';
        ELSIF v029_seed_count <> 3 THEN
            RAISE EXCEPTION
                'V029 迁移处于部分执行状态（seed anchors=%），拒绝自动改写历史',
                v029_seed_count;
        END IF;
    END IF;
END $$;
