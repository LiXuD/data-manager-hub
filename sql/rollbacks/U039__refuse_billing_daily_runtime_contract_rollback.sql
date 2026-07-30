DO $$
BEGIN
    RAISE EXCEPTION
        'billing_daily 运行时字段及唯一索引已被计费写入链路使用，禁止原地回滚；请使用迁移前备份恢复，或新增前向迁移修订';
END $$;
