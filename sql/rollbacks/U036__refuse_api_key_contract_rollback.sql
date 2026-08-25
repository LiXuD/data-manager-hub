DO $$
BEGIN
    RAISE EXCEPTION '禁止恢复已废弃的 API Key 兼容字段；请通过新的前向迁移修订运行时契约';
END $$;

