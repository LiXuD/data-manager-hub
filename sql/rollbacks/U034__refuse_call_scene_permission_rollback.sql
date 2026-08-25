DO $$
BEGIN
    RAISE EXCEPTION '禁止原地回滚场景管理权限；请通过新的前向迁移调整权限目录';
END $$;
