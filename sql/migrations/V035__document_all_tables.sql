-- Add table-level documentation without changing data, columns, indexes, or constraints.
-- Existing non-empty comments are preserved so this migration is safe for installations
-- that already maintain more specific local descriptions.
DO $$
DECLARE
    target RECORD;
    target_oid OID;
    undocumented_tables TEXT;
BEGIN
    FOR target IN
        SELECT *
        FROM (VALUES
            ('public', 'tenant_info', '租户信息表'),
            ('public', 'vendor_info', '数据服务厂商信息表'),
            ('public', 'data_type', '数据类型定义表'),
            ('public', 'vendor_config', '厂商接口调用配置表'),
            ('public', 'vendor_interface_security_step', '厂商接口安全处理步骤表'),
            ('public', 'vendor_interface_security_version', '厂商接口安全配置版本表'),
            ('public', 'caller_info', '内部系统（调用方）信息表'),
            ('public', 'caller_product', '内部系统产品配置表'),
            ('public', 'api_key', '内部系统 API Key 表'),
            ('public', 'api_key_product', 'API Key 产品授权关联表'),
            ('public', 'call_scene', '接口调用场景字典表'),
            ('public', 'call_record', '接口调用记录分区主表'),
            ('public', 'billing_daily', '租户接口调用日汇总计费表'),
            ('public', 'billing_daily_event', '日计费消费事件幂等记录表'),
            ('public', 'user_info', '用户信息表'),
            ('public', 'role_info', '角色信息表'),
            ('public', 'user_role', '用户角色关联表'),
            ('public', 'alert_rule', '监控告警规则表'),
            ('public', 'alert_record', '监控告警记录表'),
            ('public', 'circuit_breaker', '服务熔断器状态表'),
            ('public', 'operation_log', '用户操作审计日志表'),
            ('public', 'vendor_config_extended', '厂商扩展配置表'),
            ('public', 'gray_rule', '灰度发布规则表'),
            ('public', 'data_lineage', '数据血缘关系表'),
            ('public', 'quality_rule', '数据质量规则表'),
            ('public', 'quality_score', '数据质量评分记录表'),
            ('public', 'api_interface', '接口定义表'),
            ('public', 'billing_reconciliation', '计费对账记录表'),
            ('public', 'permission', '权限表'),
            ('public', 'role_permission', '角色权限关联表'),
            ('public', 'user_caller', '用户与内部系统关联表'),
            ('public', 'api_key_interface', 'API Key 接口授权表'),
            ('public', 'interface_param', '接口请求与响应参数定义表'),
            ('public', 'service_health_check', '服务健康检查结果表'),
            ('public', 'encryption_key', '字段加密密钥元数据表'),
            ('public', 'billing_template', '计费模板定义表'),
            ('public', 'billing_plan', '版本化计费方案表'),
            ('public', 'billing_plan_tier', '计费方案阶梯价格表'),
            ('public', 'billing_usage_balance', '计费周期用量余额表'),
            ('public', 'billing_event', '不可变计费事件账本'),
            ('public', 'tenant_budget', '租户月度预算与告警阈值表'),
            ('public', 'encrypted_field', '敏感字段加密配置表'),
            ('public', 'masking_rule', '数据脱敏规则表'),
            ('public', 'config_version', '平台配置版本历史表'),
            ('public', 'vendor_params_mapping', '厂商接口参数映射表'),
            ('public', 'api_permission_application', 'API 接口权限申请主表'),
            ('public', 'api_permission_application_item', 'API 接口权限申请项表'),
            ('public', 'api_permission_action', 'API 接口权限不可变审批轨迹表'),
            ('public', 'api_approval_process_config', '审批业务到流程定义的路由配置表'),
            ('public', 'databasechangelog', 'Liquibase 数据库变更记录表'),
            ('public', 'databasechangeloglock', 'Liquibase 数据库变更锁表'),
            ('workflow', 'act_evt_log', 'Flowable 运行事件日志表'),
            ('workflow', 'act_ge_bytearray', 'Flowable 通用二进制资源表'),
            ('workflow', 'act_ge_property', 'Flowable 引擎属性与版本表'),
            ('workflow', 'act_hi_actinst', 'Flowable 历史活动实例表'),
            ('workflow', 'act_hi_attachment', 'Flowable 历史附件表'),
            ('workflow', 'act_hi_comment', 'Flowable 历史评论表'),
            ('workflow', 'act_hi_detail', 'Flowable 历史变量明细表'),
            ('workflow', 'act_hi_entitylink', 'Flowable 历史实体关联表'),
            ('workflow', 'act_hi_identitylink', 'Flowable 历史身份关联表'),
            ('workflow', 'act_hi_procinst', 'Flowable 历史流程实例表'),
            ('workflow', 'act_hi_taskinst', 'Flowable 历史任务实例表'),
            ('workflow', 'act_hi_tsk_log', 'Flowable 历史任务日志表'),
            ('workflow', 'act_hi_varinst', 'Flowable 历史变量实例表'),
            ('workflow', 'act_procdef_info', 'Flowable 流程定义扩展信息表'),
            ('workflow', 'act_re_deployment', 'Flowable 流程部署记录表'),
            ('workflow', 'act_re_model', 'Flowable 流程模型表'),
            ('workflow', 'act_re_procdef', 'Flowable 流程定义表'),
            ('workflow', 'act_ru_actinst', 'Flowable 运行中活动实例表'),
            ('workflow', 'act_ru_deadletter_job', 'Flowable 死信作业表'),
            ('workflow', 'act_ru_entitylink', 'Flowable 运行时实体关联表'),
            ('workflow', 'act_ru_event_subscr', 'Flowable 运行时事件订阅表'),
            ('workflow', 'act_ru_execution', 'Flowable 运行时流程执行实例表'),
            ('workflow', 'act_ru_external_job', 'Flowable 外部工作者作业表'),
            ('workflow', 'act_ru_history_job', 'Flowable 历史异步作业表'),
            ('workflow', 'act_ru_identitylink', 'Flowable 运行时身份关联表'),
            ('workflow', 'act_ru_job', 'Flowable 可执行异步作业表'),
            ('workflow', 'act_ru_suspended_job', 'Flowable 已挂起作业表'),
            ('workflow', 'act_ru_task', 'Flowable 运行时用户任务表'),
            ('workflow', 'act_ru_timer_job', 'Flowable 定时作业表'),
            ('workflow', 'act_ru_variable', 'Flowable 运行时变量表'),
            ('workflow', 'flw_ru_batch', 'Flowable 运行时批处理任务表'),
            ('workflow', 'flw_ru_batch_part', 'Flowable 运行时批处理分片表')
        ) AS table_comments(schema_name, table_name, description)
    LOOP
        target_oid := to_regclass(format('%I.%I', target.schema_name, target.table_name));
        IF target_oid IS NOT NULL AND obj_description(target_oid, 'pg_class') IS NULL THEN
            EXECUTE format(
                'COMMENT ON TABLE %I.%I IS %L',
                target.schema_name,
                target.table_name,
                target.description
            );
        END IF;
    END LOOP;

    FOR target IN
        SELECT child.oid,
               child_namespace.nspname AS schema_name,
               child.relname AS table_name
        FROM pg_inherits inheritance
        JOIN pg_class parent ON parent.oid = inheritance.inhparent
        JOIN pg_namespace parent_namespace ON parent_namespace.oid = parent.relnamespace
        JOIN pg_class child ON child.oid = inheritance.inhrelid
        JOIN pg_namespace child_namespace ON child_namespace.oid = child.relnamespace
        WHERE parent_namespace.nspname = 'public'
          AND parent.relname = 'call_record'
    LOOP
        IF obj_description(target.oid, 'pg_class') IS NULL THEN
            EXECUTE format(
                'COMMENT ON TABLE %I.%I IS %L',
                target.schema_name,
                target.table_name,
                '接口调用记录月度分区表（' || target.table_name || '）'
            );
        END IF;
    END LOOP;

    SELECT string_agg(format('%I.%I', namespace.nspname, relation.relname), ', ' ORDER BY namespace.nspname, relation.relname)
    INTO undocumented_tables
    FROM pg_class relation
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE relation.relkind IN ('r', 'p')
      AND namespace.nspname IN ('public', 'workflow')
      AND obj_description(relation.oid, 'pg_class') IS NULL;

    IF undocumented_tables IS NOT NULL THEN
        RAISE EXCEPTION '仍有数据表缺少 COMMENT: %', undocumented_tables;
    END IF;
END $$;

-- Ensure partitions created after this migration also receive a table description.
CREATE OR REPLACE FUNCTION public.create_monthly_partition(partition_date DATE)
RETURNS VOID AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    start_date := DATE_TRUNC('month', partition_date);
    end_date := start_date + INTERVAL '1 month';
    partition_name := 'call_record_' || TO_CHAR(start_date, 'YYYY_MM');

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS public.%I PARTITION OF public.call_record FOR VALUES FROM (%L) TO (%L)',
        partition_name, start_date, end_date
    );
    EXECUTE format(
        'COMMENT ON TABLE public.%I IS %L',
        partition_name,
        '接口调用记录月度分区表（' || partition_name || '）'
    );
END;
$$ LANGUAGE plpgsql;
