package com.dataplatform.access.approval.engine;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FormProperty;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class FlowableApprovalEngineAdapter implements ApprovalEnginePort {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;

    public FlowableApprovalEngineAdapter(
            RuntimeService runtimeService,
            TaskService taskService,
            RepositoryService repositoryService,
            HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.repositoryService = repositoryService;
        this.historyService = historyService;
    }

    @Override
    public StartResult start(
            String processDefinitionKey,
            String businessKey,
            Long tenantId,
            Map<String, Object> variables) {
        ProcessInstance process = runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(processDefinitionKey)
                .businessKey(businessKey)
                .tenantId(String.valueOf(tenantId))
                .fallbackToDefaultTenant()
                .overrideProcessDefinitionTenantId(String.valueOf(tenantId))
                .variables(variables)
                .start();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(process.getProcessDefinitionId())
                .singleResult();
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(process.getId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
        return new StartResult(
                process.getId(),
                definition.getKey(),
                definition.getVersion(),
                activeTasks.size() == 1 ? toSnapshot(activeTasks.getFirst()) : null);
    }

    @Override
    public List<TaskSnapshot> findTasks(String userId, Set<String> candidateGroups) {
        Map<String, TaskSnapshot> tasks = new LinkedHashMap<>();
        taskService.createTaskQuery()
                .active()
                .taskAssignee(userId)
                .orderByTaskCreateTime()
                .desc()
                .list()
                .forEach(task -> tasks.put(task.getId(), toSnapshot(task)));
        if (candidateGroups != null && !candidateGroups.isEmpty()) {
            taskService.createTaskQuery()
                    .active()
                    .taskUnassigned()
                    .taskCandidateGroupIn(new ArrayList<>(candidateGroups))
                    .orderByTaskCreateTime()
                    .desc()
                    .list()
                    .forEach(task -> tasks.put(task.getId(), toSnapshot(task)));
        }
        return List.copyOf(tasks.values());
    }

    @Override
    public Optional<TaskSnapshot> getTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        return Optional.ofNullable(task).map(this::toSnapshot);
    }

    @Override
    public Optional<TaskSnapshot> getCurrentTask(String processInstanceId) {
        List<TaskSnapshot> tasks = getCurrentTasks(processInstanceId);
        return tasks.size() == 1 ? Optional.of(tasks.getFirst()) : Optional.empty();
    }

    @Override
    public List<TaskSnapshot> getCurrentTasks(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public TaskPolicy getTaskPolicy(String taskId) {
        Task task = requiredTask(taskId);
        BpmnModel model = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        FlowElement element = model.getFlowElement(task.getTaskDefinitionKey());
        if (!(element instanceof UserTask userTask)) {
            throw new IllegalStateException("审批节点定义不存在");
        }
        boolean allowWithdraw = booleanProperty(userTask, "_allowWithdraw");
        boolean allowExpireAdjustment = booleanProperty(userTask, "_allowExpireAdjustment");
        Set<String> decisions = userTask.getFormProperties().stream()
                .filter(property -> "decision".equals(property.getId()))
                .flatMap(property -> property.getFormValues().stream())
                .map(value -> value.getId())
                .filter(this::hasText)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<FormField> fields = userTask.getFormProperties().stream()
                .filter(property -> !property.getId().startsWith("_"))
                .filter(property -> !"decision".equals(property.getId()))
                .filter(FormProperty::isWriteable)
                .map(property -> new FormField(
                        property.getId(),
                        property.getName(),
                        property.getType(),
                        property.isRequired(),
                        property.getDefaultExpression(),
                        property.getFormValues().stream()
                                .map(value -> new FormOption(value.getId(), value.getName()))
                                .toList()))
                .toList();
        return new TaskPolicy(allowWithdraw, allowExpireAdjustment, decisions, fields);
    }

    @Override
    public boolean canClaim(String taskId, Set<String> candidateGroups) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .active()
                .taskUnassigned()
                .singleResult();
        if (task == null || candidateGroups == null || candidateGroups.isEmpty()) {
            return false;
        }
        return taskService.getIdentityLinksForTask(taskId).stream()
                .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()))
                .map(IdentityLink::getGroupId)
                .anyMatch(candidateGroups::contains);
    }

    @Override
    public void claim(String taskId, String userId) {
        taskService.claim(taskId, userId);
    }

    @Override
    public void unclaim(String taskId, String userId) {
        Task task = requiredTask(taskId);
        if (!userId.equals(task.getAssignee())) {
            throw new IllegalStateException("只能释放本人已认领的任务");
        }
        taskService.unclaim(taskId);
    }

    @Override
    public void complete(String taskId, String userId, Map<String, Object> variables) {
        Task task = requiredTask(taskId);
        if (!userId.equals(task.getAssignee())) {
            throw new IllegalStateException("任务必须由当前用户认领后完成");
        }
        taskService.complete(taskId, variables);
    }

    @Override
    public void terminate(String processInstanceId, String reason) {
        ProcessInstance process = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (process != null) {
            runtimeService.deleteProcessInstance(processInstanceId, reason);
        }
    }

    @Override
    public List<HistorySnapshot> history(String processInstanceId) {
        return historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list()
                .stream()
                .map(this::toHistory)
                .toList();
    }

    private Task requiredTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        if (task == null) {
            throw new IllegalStateException("审批任务不存在或已完成");
        }
        return task;
    }

    private TaskSnapshot toSnapshot(Task task) {
        return new TaskSnapshot(
                task.getId(),
                task.getProcessInstanceId(),
                task.getProcessDefinitionId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                task.getAssignee(),
                toLocalDateTime(task.getCreateTime()));
    }

    private HistorySnapshot toHistory(HistoricActivityInstance activity) {
        return new HistorySnapshot(
                activity.getActivityId(),
                activity.getActivityName(),
                activity.getActivityType(),
                activity.getTaskId(),
                activity.getAssignee(),
                toLocalDateTime(activity.getStartTime()),
                toLocalDateTime(activity.getEndTime()));
    }

    private boolean booleanProperty(UserTask task, String id) {
        return task.getFormProperties().stream()
                .filter(property -> id.equals(property.getId()))
                .map(FormProperty::getDefaultExpression)
                .filter(this::hasText)
                .map(Boolean::parseBoolean)
                .findFirst()
                .orElse(false);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDateTime toLocalDateTime(java.util.Date value) {
        return value == null
                ? null
                : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }
}
