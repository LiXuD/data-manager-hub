-- 仅在尚未产生权限、调用或计费事实时允许移除 V029。

DO $$
DECLARE
    target_interface_id BIGINT;
BEGIN
    SELECT id INTO target_interface_id
    FROM api_interface
    WHERE interface_code = 'PROGRAMMER_HISTORY_BY_DATE';

    IF target_interface_id IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM api_key_interface
        WHERE interface_id = target_interface_id
    ) OR EXISTS (
        SELECT 1 FROM api_permission_application_item
        WHERE interface_id = target_interface_id
    ) THEN
        RAISE EXCEPTION '指定日期程序员历史接口已产生权限事实，禁止回滚 V029';
    END IF;

    IF EXISTS (
        SELECT 1 FROM call_record
        WHERE api_code = 'PROGRAMMER_HISTORY_BY_DATE'
    ) OR EXISTS (
        SELECT 1 FROM billing_event
        WHERE interface_code = 'PROGRAMMER_HISTORY_BY_DATE'
    ) THEN
        RAISE EXCEPTION '指定日期程序员历史接口已产生调用或计费事实，禁止回滚 V029';
    END IF;

    IF EXISTS (
        SELECT 1 FROM billing_plan
        WHERE interface_code = 'PROGRAMMER_HISTORY_BY_DATE'
          AND plan_code <> 'UAPI-PROGRAMMER-HISTORY-BY-DATE'
    ) THEN
        RAISE EXCEPTION '指定日期程序员历史接口存在额外计费方案，禁止回滚 V029';
    END IF;
END $$;

DELETE FROM billing_usage_balance
WHERE plan_id IN (
    SELECT id FROM billing_plan
    WHERE plan_code = 'UAPI-PROGRAMMER-HISTORY-BY-DATE'
);

DELETE FROM billing_plan_tier
WHERE plan_id IN (
    SELECT id FROM billing_plan
    WHERE plan_code = 'UAPI-PROGRAMMER-HISTORY-BY-DATE'
);

DELETE FROM billing_plan
WHERE plan_code = 'UAPI-PROGRAMMER-HISTORY-BY-DATE';

DELETE FROM vendor_config
WHERE interface_id IN (
    SELECT id FROM api_interface
    WHERE interface_code = 'PROGRAMMER_HISTORY_BY_DATE'
);

DELETE FROM interface_param
WHERE interface_id IN (
    SELECT id FROM api_interface
    WHERE interface_code = 'PROGRAMMER_HISTORY_BY_DATE'
);

DELETE FROM api_interface
WHERE interface_code = 'PROGRAMMER_HISTORY_BY_DATE';

DELETE FROM data_type
WHERE data_type_code = 'programmer_history_by_date';
