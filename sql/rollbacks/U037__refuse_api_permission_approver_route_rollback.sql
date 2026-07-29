DO $$
BEGIN
    RAISE EXCEPTION
        'V037 可能已产生或办理 api_interface_approver 候选任务，禁止原地回滚；请使用迁移前备份恢复，或新增前向迁移调整审批角色';
END $$;
