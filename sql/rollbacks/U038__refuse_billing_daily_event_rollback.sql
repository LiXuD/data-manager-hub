DO $$
BEGIN
    RAISE EXCEPTION
        'billing_daily_event 保存日账单投影幂等状态，禁止原地回滚；请使用迁移前备份恢复，或新增前向迁移修订';
END $$;
