-- Route API permission approvals to the dedicated approver role and repair
-- still-active, unclaimed tasks created with the legacy admin candidate group.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM role_info
        WHERE role_code = 'api_interface_approver'
          AND status = 'active'
          AND deleted = FALSE
    ) THEN
        RAISE EXCEPTION '缺少有效角色 api_interface_approver，无法迁移接口权限审批路由';
    END IF;
END $$;

UPDATE api_approval_process_config
SET approver_group = 'api_interface_approver',
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE tenant_id = 0
  AND business_type IN ('API_PERMISSION_OPEN', 'API_PERMISSION_RENEW')
  AND risk_level = '*'
  AND engine_type = 'FLOWABLE'
  AND process_definition_key = 'apiPermissionApproval'
  AND approver_group = 'admin'
  AND priority = 0;

UPDATE workflow.act_ru_identitylink identity_link
SET group_id_ = 'api_interface_approver'
FROM api_permission_application application,
     workflow.act_ru_task task
WHERE task.id_ = application.current_task_id
  AND identity_link.task_id_ = task.id_
  AND application.status = 'IN_REVIEW'
  AND application.engine_status = 'RUNNING'
  AND application.process_definition_key = 'apiPermissionApproval'
  AND application.current_task_key = 'apiPermissionApprovalTask'
  AND task.assignee_ IS NULL
  AND identity_link.type_ = 'candidate'
  AND identity_link.user_id_ IS NULL
  AND identity_link.group_id_ = 'admin';

UPDATE workflow.act_hi_identitylink identity_link
SET group_id_ = 'api_interface_approver'
FROM api_permission_application application,
     workflow.act_ru_task task
WHERE task.id_ = application.current_task_id
  AND identity_link.task_id_ = task.id_
  AND application.status = 'IN_REVIEW'
  AND application.engine_status = 'RUNNING'
  AND application.process_definition_key = 'apiPermissionApproval'
  AND application.current_task_key = 'apiPermissionApprovalTask'
  AND task.assignee_ IS NULL
  AND identity_link.type_ = 'candidate'
  AND identity_link.user_id_ IS NULL
  AND identity_link.group_id_ = 'admin';

UPDATE workflow.act_ru_variable process_variable
SET text_ = 'api_interface_approver'
FROM api_permission_application application,
     workflow.act_ru_task task
WHERE task.id_ = application.current_task_id
  AND process_variable.proc_inst_id_ = application.process_instance_id
  AND application.status = 'IN_REVIEW'
  AND application.engine_status = 'RUNNING'
  AND application.process_definition_key = 'apiPermissionApproval'
  AND application.current_task_key = 'apiPermissionApprovalTask'
  AND task.assignee_ IS NULL
  AND process_variable.name_ = 'approverGroup'
  AND process_variable.type_ = 'string'
  AND process_variable.text_ = 'admin';

UPDATE workflow.act_hi_varinst process_variable
SET text_ = 'api_interface_approver'
FROM api_permission_application application,
     workflow.act_ru_task task
WHERE task.id_ = application.current_task_id
  AND process_variable.proc_inst_id_ = application.process_instance_id
  AND application.status = 'IN_REVIEW'
  AND application.engine_status = 'RUNNING'
  AND application.process_definition_key = 'apiPermissionApproval'
  AND application.current_task_key = 'apiPermissionApprovalTask'
  AND task.assignee_ IS NULL
  AND process_variable.name_ = 'approverGroup'
  AND process_variable.var_type_ = 'string'
  AND process_variable.text_ = 'admin';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM api_approval_process_config
        WHERE tenant_id = 0
          AND business_type IN ('API_PERMISSION_OPEN', 'API_PERMISSION_RENEW')
          AND risk_level = '*'
          AND engine_type = 'FLOWABLE'
          AND process_definition_key = 'apiPermissionApproval'
          AND approver_group = 'admin'
          AND priority = 0
    ) THEN
        RAISE EXCEPTION '接口权限审批默认路由仍指向 admin';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM api_permission_application application
        JOIN workflow.act_ru_task task
          ON task.id_ = application.current_task_id
        JOIN workflow.act_ru_identitylink identity_link
          ON identity_link.task_id_ = task.id_
        WHERE application.status = 'IN_REVIEW'
          AND application.engine_status = 'RUNNING'
          AND application.process_definition_key = 'apiPermissionApproval'
          AND application.current_task_key = 'apiPermissionApprovalTask'
          AND task.assignee_ IS NULL
          AND identity_link.type_ = 'candidate'
          AND identity_link.group_id_ = 'admin'
    ) THEN
        RAISE EXCEPTION '仍存在使用 admin 候选组的未认领接口权限审批任务';
    END IF;
END $$;
