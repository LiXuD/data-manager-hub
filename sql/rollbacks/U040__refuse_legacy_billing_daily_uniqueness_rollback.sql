DO $$
BEGIN
    RAISE EXCEPTION
        '恢复 caller/day 唯一约束可能与当前多维日汇总数据冲突，禁止原地回滚；请使用迁移前备份恢复';
END $$;
