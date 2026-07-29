DO $$
BEGIN
    RAISE EXCEPTION '禁止原地回滚数据表 COMMENT；请通过新的前向迁移修订描述';
END $$;
