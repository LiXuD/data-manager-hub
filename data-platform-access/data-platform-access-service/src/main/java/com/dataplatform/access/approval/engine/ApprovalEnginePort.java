package com.dataplatform.access.approval.engine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 审批引擎端口。业务层只依赖该接口，Flowable/Camunda 适配器不得泄漏原生对象。
 */
public interface ApprovalEnginePort {

    StartResult start(
            String processDefinitionKey,
            String businessKey,
            Long tenantId,
            Map<String, Object> variables);

    List<TaskSnapshot> findTasks(String userId, Set<String> candidateGroups);

    Optional<TaskSnapshot> getTask(String taskId);

    Optional<TaskSnapshot> getCurrentTask(String processInstanceId);

    List<TaskSnapshot> getCurrentTasks(String processInstanceId);

    TaskPolicy getTaskPolicy(String taskId);

    boolean canClaim(String taskId, Set<String> candidateGroups);

    void claim(String taskId, String userId);

    void unclaim(String taskId, String userId);

    void complete(String taskId, String userId, Map<String, Object> variables);

    void terminate(String processInstanceId, String reason);

    List<HistorySnapshot> history(String processInstanceId);

    List<ProcessDefinitionSnapshot> processDiagnostics();

    record StartResult(
            String processInstanceId,
            String processDefinitionKey,
            Integer processDefinitionVersion,
            TaskSnapshot currentTask) {
    }

    record TaskSnapshot(
            String id,
            String processInstanceId,
            String processDefinitionId,
            String taskDefinitionKey,
            String name,
            String assignee,
            LocalDateTime createdAt) {
    }

    record TaskPolicy(
            boolean allowWithdraw,
            boolean allowExpireAdjustment,
            Set<String> allowedDecisions,
            List<FormField> formFields) {
    }

    record FormField(
            String id,
            String name,
            String type,
            boolean required,
            String defaultValue,
            List<FormOption> options) {
    }

    record FormOption(String value, String label) {
    }

    record HistorySnapshot(
            String activityId,
            String activityName,
            String activityType,
            String taskId,
            String assignee,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {
    }

    record ProcessDefinitionSnapshot(
            String id,
            String key,
            String name,
            Integer version,
            boolean suspended,
            List<ProcessNodeSnapshot> nodes,
            List<String> boundRoles,
            long activeInstances,
            long totalInstances) {
    }

    record ProcessNodeSnapshot(
            String id,
            String name,
            String type,
            List<String> candidateGroups) {
    }
}
