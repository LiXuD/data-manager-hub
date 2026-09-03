package com.dataplatform.access.approval.service;

import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.ApprovalTaskDetailResponse;
import com.dataplatform.access.approval.api.ApprovalTaskResponse;
import com.dataplatform.access.approval.api.CompleteTaskRequest;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApplicationStatus;
import com.dataplatform.access.approval.engine.ApprovalEnginePort;
import com.dataplatform.api.Result;
import com.dataplatform.common.security.RoleCodeNormalizer;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.identity.api.feign.IdentityAccessInternalFeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class ApiPermissionTaskService {

    private static final Pattern FORM_FIELD_NAME =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");

    private final ApprovalEnginePort approvalEngine;
    private final ApiPermissionApplicationService applicationService;
    private final IdentityAccessInternalFeignClient identityClient;
    private final TransactionTemplate transactionTemplate;

    public ApiPermissionTaskService(
            ApprovalEnginePort approvalEngine,
            ApiPermissionApplicationService applicationService,
            IdentityAccessInternalFeignClient identityClient,
            PlatformTransactionManager transactionManager) {
        this.approvalEngine = approvalEngine;
        this.applicationService = applicationService;
        this.identityClient = identityClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<ApprovalTaskResponse> listTasks(Long userId, Long tenantId) {
        requireUserScope(userId);
        boolean platformAdmin = isPlatformAdmin();
        requireTenantScope(tenantId, platformAdmin);
        Set<String> roles = roleCodes(userId);
        List<ApprovalEnginePort.TaskSnapshot> tasks = requireTaskList(
                callFindTasks(userId, roles), "审批任务查询失败");
        return tasks.stream()
                .map(task -> new ApprovalTaskResponse(task, findTaskApplication(task)))
                .filter(response -> response.application() != null)
                .filter(response -> platformAdmin
                        || tenantId.equals(response.application().getTenantId()))
                .toList();
    }

    public ApprovalTaskDetailResponse taskDetail(String taskId, Long userId, Long tenantId) {
        requireUserScope(userId);
        ApprovalEnginePort.TaskSnapshot task = requireTask(taskId);
        ApiPermissionApplication application = requireTaskApplication(task, tenantId);
        Set<String> roles = roleCodes(userId);
        boolean assigned = String.valueOf(userId).equals(task.assignee());
        if (!assigned && !canClaim(taskId, roles)) {
            throw forbidden("TASK_ACCESS_DENIED", "当前用户不是任务办理人或候选组成员");
        }
        return new ApprovalTaskDetailResponse(
                task,
                requireTaskPolicy(taskId),
                applicationService.detail(application.getId()));
    }

    public ApprovalTaskResponse claim(String taskId, Long userId, Long tenantId) {
        requireUserScope(userId);
        ApprovalEnginePort.TaskSnapshot task = requireTask(taskId);
        ApiPermissionApplication application = requireTaskApplication(task, tenantId);
        requireReviewState(application);
        if (userId.equals(application.getApplicantUserId())) {
            throw forbidden("SELF_APPROVAL_FORBIDDEN", "申请人不能认领自己的审批任务");
        }
        if (String.valueOf(userId).equals(task.assignee())) {
            return new ApprovalTaskResponse(task, application);
        }
        if (task.assignee() != null || !canClaim(taskId, roleCodes(userId))) {
            throw forbidden("TASK_CLAIM_DENIED", "当前用户不在任务候选组或任务已被认领");
        }
        try {
            approvalEngine.claim(taskId, String.valueOf(userId));
        } catch (RuntimeException exception) {
            throw conflict("TASK_STATE_CONFLICT", "任务已被其他审批人认领");
        }
        ApprovalEnginePort.TaskSnapshot claimed = requireTask(taskId);
        projectActiveTasks(application);
        applicationService.updateApplication(application);
        return new ApprovalTaskResponse(claimed, application);
    }

    public void unclaim(String taskId, Long userId, Long tenantId) {
        requireUserScope(userId);
        ApprovalEnginePort.TaskSnapshot task = requireTask(taskId);
        ApiPermissionApplication application = requireTaskApplication(task, tenantId);
        requireReviewState(application);
        if (!String.valueOf(userId).equals(task.assignee())) {
            throw forbidden("TASK_UNCLAIM_DENIED", "只能释放本人已认领的任务");
        }
        try {
            approvalEngine.unclaim(taskId, String.valueOf(userId));
        } catch (RuntimeException exception) {
            throw conflict("TASK_STATE_CONFLICT", "任务状态已变化，请刷新后重试");
        }
        requireTask(taskId);
        projectActiveTasks(application);
        applicationService.updateApplication(application);
    }

    public ApiPermissionApplication complete(
            String taskId,
            CompleteTaskRequest request,
            Long userId,
            String username,
            Long tenantId) {
        requireUserScope(userId);
        ApprovalEnginePort.TaskSnapshot task = requireTask(taskId);
        ApprovalEnginePort.TaskPolicy policy = requireTaskPolicy(taskId);
        validateCompleteRequest(request, policy);
        ApiPermissionApplication application = requireTaskApplication(task, tenantId);
        requireReviewState(application);
        if (userId.equals(application.getApplicantUserId())) {
            throw forbidden("SELF_APPROVAL_FORBIDDEN", "申请人不能审批自己的申请");
        }
        if (!String.valueOf(userId).equals(task.assignee())) {
            throw conflict("TASK_NOT_CLAIMED", "请先认领任务后再审批");
        }
        if (request.applicationVersion() == null
                || !request.applicationVersion().equals(application.getVersion())) {
            throw conflict("APPLICATION_VERSION_CONFLICT", "申请已被更新，请刷新后重试");
        }
        LocalDateTime approvedExpireAt = resolveApprovedExpireAt(application, request, policy);
        ApprovedCachePolicy approvedCachePolicy =
                resolveApprovedCachePolicy(application, request);
        if ("APPROVE".equals(request.decision())) {
            applicationService.validateProvisioningResources(application);
        }

        return transactionTemplate.execute(status -> {
            ApprovalEnginePort.TaskSnapshot activeTask = requireTask(taskId);
            ApiPermissionApplication locked = requireTaskApplication(activeTask, tenantId);
            requireReviewState(locked);
            if (!request.applicationVersion().equals(locked.getVersion())) {
                throw conflict("APPLICATION_VERSION_CONFLICT", "申请已被更新，请刷新后重试");
            }

            locked.setApprovedExpireAt(approvedExpireAt);
            locked.setDecidedBy(userId);
            locked.setDecidedByNameSnapshot(username);
            locked.setDecidedAt(LocalDateTime.now());
            locked.setDecisionComment(normalize(request.comment()));
            applicationService.updateApplication(locked);
            applicationService.applyApprovedCachePolicy(
                    locked.getId(),
                    approvedCachePolicy.enabled(),
                    approvedCachePolicy.days());
            applicationService.appendAction(
                    locked,
                    request.decision(),
                    "USER",
                    userId,
                    username,
                    ApplicationStatus.IN_REVIEW.name(),
                    ApplicationStatus.IN_REVIEW.name(),
                    normalize(request.comment()),
                    activeTask);

            Map<String, Object> variables = new HashMap<>();
            variables.put("decision", request.decision());
            variables.put("approverUserId", userId);
            variables.put("approverName", username);
            variables.put("approvalComment", normalize(request.comment()));
            variables.put("approvedExpireAt", approvedExpireAt != null
                    ? approvedExpireAt.toString()
                    : null);
            variables.put("approvedCacheEnabled", approvedCachePolicy.enabled());
            variables.put("approvedCacheDays", approvedCachePolicy.days());
            variables.put("approvalFormData", safeFormData(request.formData()));
            try {
                approvalEngine.complete(taskId, String.valueOf(userId), variables);
            } catch (ApiPermissionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw conflict("WORKFLOW_COMPLETE_FAILED", "审批流程推进失败，请刷新后重试");
            }

            ApiPermissionApplication updated = applicationService.requireApplication(locked.getId());
            if (ApplicationStatus.IN_REVIEW.name().equals(updated.getStatus())) {
                ApprovalEnginePort.TaskSnapshot next = requireCurrentTaskOrEmpty(
                        updated.getProcessInstanceId());
                applicationService.projectCurrentTask(updated, next);
                applicationService.updateApplication(updated);
            }
            return applicationService.requireApplication(locked.getId());
        });
    }

    public List<ApprovalEnginePort.HistorySnapshot> processHistory(
            Long applicationId,
            Long userId,
            Long tenantId,
            boolean tenantScope) {
        requireUserScope(userId);
        requireTenantScope(tenantId, isPlatformAdmin());
        ApiPermissionApplication application = applicationService.requireVisibleApplication(
                applicationId, userId, tenantId, tenantScope);
        if (application.getProcessInstanceId() == null
                || application.getProcessInstanceId().isBlank()) {
            return List.of();
        }
        return requireHistory(application.getProcessInstanceId());
    }

    public List<ApprovalEnginePort.ProcessDefinitionSnapshot> processDiagnostics() {
        try {
            List<ApprovalEnginePort.ProcessDefinitionSnapshot> definitions =
                    approvalEngine.processDiagnostics();
            if (definitions == null || definitions.stream().anyMatch(Objects::isNull)) {
                throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎返回了无效流程诊断数据");
            }
            return definitions;
        } catch (ApiPermissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批流程诊断暂不可用");
        }
    }

    private ApprovalEnginePort.TaskSnapshot requireTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw badRequest("TASK_ID_REQUIRED", "审批任务标识不能为空");
        }
        try {
            Optional<ApprovalEnginePort.TaskSnapshot> task = approvalEngine.getTask(taskId);
            if (task == null) {
                throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎未返回任务查询结果");
            }
            ApprovalEnginePort.TaskSnapshot snapshot = task.orElseThrow(
                    () -> conflict("TASK_NOT_FOUND", "审批任务不存在或已完成"));
            requireValidTaskSnapshot(snapshot);
            return snapshot;
        } catch (ApiPermissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批任务查询暂不可用");
        }
    }

    private ApiPermissionApplication requireTaskApplication(
            ApprovalEnginePort.TaskSnapshot task,
            Long tenantId) {
        if (task == null || task.processInstanceId() == null
                || task.processInstanceId().isBlank()) {
            throw serviceUnavailable("APPLICATION_DATA_INVALID", "审批任务缺少有效流程实例");
        }
        ApiPermissionApplication application;
        try {
            application = applicationService.findByProcessInstance(task.processInstanceId());
        } catch (ApiPermissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw serviceUnavailable("APPLICATION_LOOKUP_UNAVAILABLE", "审批申请查询失败");
        }
        if (application == null) {
            throw conflict("APPLICATION_PROCESS_MISMATCH", "审批任务未关联有效申请");
        }
        if (application.getTenantId() == null || application.getTenantId() <= 0) {
            throw serviceUnavailable("APPLICATION_DATA_INVALID", "审批申请缺少有效租户信息");
        }
        if (!isPlatformAdmin() && (tenantId == null
                || !tenantId.equals(application.getTenantId()))) {
            throw forbidden("TASK_TENANT_DENIED", "不能处理其他租户的审批任务");
        }
        return application;
    }

    private void requireReviewState(ApiPermissionApplication application) {
        if (application == null || application.getStatus() == null) {
            throw conflict("APPLICATION_DATA_INVALID", "审批申请状态数据不完整");
        }
        if (!ApplicationStatus.IN_REVIEW.name().equals(application.getStatus())) {
            throw conflict("APPLICATION_STATE_CONFLICT", "申请已不在审批中");
        }
    }

    private Set<String> roleCodes(Long userId) {
        requireUserScope(userId);
        Result<List<String>> result;
        try {
            result = identityClient.getRoleCodes(userId);
        } catch (RuntimeException exception) {
            throw serviceUnavailable("DEPENDENCY_UNAVAILABLE", "身份服务暂不可用");
        }
        if (result == null || result.getCode() == null
                || result.getCode() != 200 || result.getData() == null) {
            throw serviceUnavailable("DEPENDENCY_UNAVAILABLE", "身份服务返回异常");
        }
        return RoleCodeNormalizer.normalizeAll(result.getData());
    }

    private void validateCompleteRequest(
            CompleteTaskRequest request,
            ApprovalEnginePort.TaskPolicy policy) {
        if (request == null || policy == null
                || !policy.allowedDecisions().contains(request.decision())) {
            throw badRequest("INVALID_DECISION", "decision 不在当前审批节点允许的决定范围内");
        }
        if ("REJECT".equals(request.decision())
                && (request.comment() == null || request.comment().isBlank())) {
            throw badRequest("REJECT_COMMENT_REQUIRED", "驳回原因不能为空");
        }
        if (request.comment() != null && request.comment().length() > 2000) {
            throw badRequest("COMMENT_TOO_LONG", "审批意见不能超过 2000 字");
        }
        if (request.formData() != null && request.formData().size() > 20) {
            throw badRequest("FORM_DATA_TOO_LARGE", "节点扩展字段不能超过 20 项");
        }
        Map<String, ApprovalEnginePort.FormField> fields = policy.formFields().stream()
                .collect(Collectors.toUnmodifiableMap(ApprovalEnginePort.FormField::id, field -> field));
        Map<String, Object> submitted = request.formData() == null ? Map.of() : request.formData();
        submitted.forEach((key, value) -> {
                if (key == null || !FORM_FIELD_NAME.matcher(key).matches()) {
                    throw badRequest("INVALID_FORM_FIELD", "节点扩展字段名格式不正确");
                }
                ApprovalEnginePort.FormField field = fields.get(key);
                if (field == null) {
                    throw badRequest("UNDECLARED_FORM_FIELD", "节点扩展字段未在流程定义中声明");
                }
                if (!matchesFieldType(field, value)) {
                    throw badRequest("INVALID_FORM_FIELD_VALUE", "节点扩展字段值类型不正确");
                }
        });
        fields.values().stream()
                .filter(ApprovalEnginePort.FormField::required)
                .filter(field -> isMissing(submitted.get(field.id())))
                .findFirst()
                .ifPresent(field -> {
                    throw badRequest("REQUIRED_FORM_FIELD_MISSING", field.name() + "不能为空");
                });
    }

    private ApprovedCachePolicy resolveApprovedCachePolicy(
            ApiPermissionApplication application,
            CompleteTaskRequest request) {
        if (!"APPROVE".equals(request.decision())) {
            return new ApprovedCachePolicy(false, null);
        }
        var items = applicationService.listItems(application.getId());
        if (items == null) {
            throw serviceUnavailable("APPLICATION_ITEM_LOOKUP_UNAVAILABLE", "申请接口项查询失败");
        }
        if (items.isEmpty() || items.stream().anyMatch(Objects::isNull)) {
            throw conflict("APPLICATION_ITEMS_MISSING", "申请未包含接口明细");
        }
        boolean requestedEnabled =
                Boolean.TRUE.equals(items.get(0).getRequestedCacheEnabled());
        Integer requestedDays = items.get(0).getRequestedCacheDays();
        if (requestedEnabled && request.approvedCacheEnabled() == null) {
            throw badRequest("CACHE_DECISION_REQUIRED", "必须明确是否批准接口结果缓存");
        }
        boolean approvedEnabled = Boolean.TRUE.equals(request.approvedCacheEnabled());
        if (!approvedEnabled) {
            if (request.approvedCacheDays() != null) {
                throw badRequest("INVALID_CACHE_APPROVAL", "不批准缓存时不能填写缓存时效");
            }
            return new ApprovedCachePolicy(false, null);
        }
        if (!requestedEnabled) {
            throw badRequest("CACHE_NOT_REQUESTED", "申请人未申请接口结果缓存");
        }
        if (request.approvedCacheDays() == null
                || request.approvedCacheDays() < 1
                || requestedDays == null
                || request.approvedCacheDays() > requestedDays) {
            throw badRequest(
                    "INVALID_CACHE_APPROVAL",
                    "批准缓存时效必须在 1 天到申请时效上限之间");
        }
        return new ApprovedCachePolicy(true, request.approvedCacheDays());
    }

    private Map<String, Object> safeFormData(Map<String, Object> formData) {
        if (formData == null || formData.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        formData.forEach((key, value) -> {
            if (value != null) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private LocalDateTime resolveApprovedExpireAt(
            ApiPermissionApplication application,
            CompleteTaskRequest request,
            ApprovalEnginePort.TaskPolicy policy) {
        if ("REJECT".equals(request.decision())) {
            return null;
        }
        if (!policy.allowExpireAdjustment()
                && request.approvedExpireAt() != null
                && !request.approvedExpireAt().equals(application.getRequestedExpireAt())) {
            throw badRequest("EXPIRY_ADJUSTMENT_NOT_ALLOWED", "当前审批节点不允许调整有效期");
        }
        LocalDateTime expireAt = request.approvedExpireAt() == null
                ? application.getRequestedExpireAt()
                : request.approvedExpireAt();
        if (expireAt == null
                || !expireAt.isAfter(LocalDateTime.now())
                || (application.getRequestedExpireAt() != null
                && expireAt.isAfter(application.getRequestedExpireAt()))) {
            throw badRequest("INVALID_APPROVED_EXPIRY", "批准有效期必须在未来且不能超过申请有效期");
        }
        return expireAt;
    }

    private void projectActiveTasks(ApiPermissionApplication application) {
        ApprovalEnginePort.TaskSnapshot current = requireCurrentTaskOrEmpty(
                application.getProcessInstanceId());
        applicationService.projectCurrentTask(application, current);
    }

    private boolean matchesFieldType(ApprovalEnginePort.FormField field, Object value) {
        if (value == null) {
            return !field.required();
        }
        Predicate<Object> validator = switch (field.type() == null ? "string" : field.type()) {
            case "boolean" -> candidate -> candidate instanceof Boolean;
            case "long" -> candidate -> candidate instanceof Byte
                    || candidate instanceof Short
                    || candidate instanceof Integer
                    || candidate instanceof Long;
            case "double" -> candidate -> candidate instanceof Number;
            case "enum" -> candidate -> candidate instanceof String text
                    && field.options().stream().anyMatch(option -> option != null
                    && option.value() != null && option.value().equals(text));
            case "date" -> candidate -> candidate instanceof String text && isIsoDateTime(text);
            default -> candidate -> candidate instanceof String text && text.length() <= 2000;
        };
        return validator.test(value);
    }

    private boolean isMissing(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private boolean isIsoDateTime(String value) {
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private ApiPermissionException badRequest(String code, String message) {
        return new ApiPermissionException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiPermissionException forbidden(String code, String message) {
        return new ApiPermissionException(HttpStatus.FORBIDDEN, code, message);
    }

    private ApiPermissionException conflict(String code, String message) {
        return new ApiPermissionException(HttpStatus.CONFLICT, code, message);
    }

    private ApiPermissionException serviceUnavailable(String code, String message) {
        return new ApiPermissionException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    private void requireUserScope(Long userId) {
        if (userId == null || userId <= 0) {
            throw forbidden("USER_SCOPE_REQUIRED", "当前用户身份不可用");
        }
    }

    private void requireTenantScope(Long tenantId, boolean platformAdmin) {
        if (!platformAdmin && (tenantId == null || tenantId <= 0)) {
            throw forbidden("TENANT_SCOPE_REQUIRED", "当前用户没有租户作用域");
        }
    }

    private boolean isPlatformAdmin() {
        try {
            return UserContext.hasPermission("system:admin");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<ApprovalEnginePort.TaskSnapshot> callFindTasks(
            Long userId,
            Set<String> roles) {
        try {
            return approvalEngine.findTasks(String.valueOf(userId), roles);
        } catch (ApiPermissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批任务查询暂不可用");
        }
    }

    private List<ApprovalEnginePort.TaskSnapshot> requireTaskList(
            List<ApprovalEnginePort.TaskSnapshot> tasks,
            String message) {
        if (tasks == null || tasks.stream().anyMatch(task -> task == null
                || !isValidTaskSnapshot(task))) {
            throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", message);
        }
        return tasks;
    }

    private ApiPermissionApplication findTaskApplication(
            ApprovalEnginePort.TaskSnapshot task) {
        if (task == null || task.processInstanceId() == null
                || task.processInstanceId().isBlank()) {
            throw serviceUnavailable("APPLICATION_DATA_INVALID", "审批任务缺少有效流程实例");
        }
        try {
            ApiPermissionApplication application = applicationService.findByProcessInstance(
                    task.processInstanceId());
            if (application != null
                    && (application.getTenantId() == null || application.getTenantId() <= 0)) {
                throw serviceUnavailable("APPLICATION_DATA_INVALID", "审批申请缺少有效租户信息");
            }
            return application;
        } catch (ApiPermissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw serviceUnavailable("APPLICATION_LOOKUP_UNAVAILABLE", "审批申请查询失败");
        }
    }

    private boolean canClaim(String taskId, Set<String> roles) {
        try {
            return approvalEngine.canClaim(taskId, roles);
        } catch (ApiPermissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批任务候选人查询暂不可用");
        }
    }

    private ApprovalEnginePort.TaskPolicy requireTaskPolicy(String taskId) {
        ApprovalEnginePort.TaskPolicy policy;
        try {
            policy = approvalEngine.getTaskPolicy(taskId);
        } catch (ApiPermissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批节点策略查询暂不可用");
        }
        if (policy == null || policy.allowedDecisions() == null
                || policy.allowedDecisions().isEmpty() || policy.formFields() == null
                || policy.formFields().size() > 20
                || policy.allowedDecisions().stream().anyMatch(decision -> decision == null
                || decision.isBlank())) {
            throw serviceUnavailable("APPROVAL_CONFIG_INVALID", "审批节点策略配置无效");
        }
        Set<String> fieldIds = new HashSet<>();
        for (ApprovalEnginePort.FormField field : policy.formFields()) {
            validateFormField(field);
            if (!fieldIds.add(field.id())) {
                throw serviceUnavailable("APPROVAL_CONFIG_INVALID", "审批扩展字段标识重复");
            }
        }
        return policy;
    }

    private void validateFormField(ApprovalEnginePort.FormField field) {
        if (field == null || field.id() == null
                || !FORM_FIELD_NAME.matcher(field.id()).matches()) {
            throw serviceUnavailable("APPROVAL_CONFIG_INVALID", "审批扩展字段配置无效");
        }
        String type = field.type() == null ? "string" : field.type();
        if (!Set.of("string", "boolean", "long", "double", "enum", "date").contains(type)) {
            throw serviceUnavailable("APPROVAL_CONFIG_INVALID", "审批扩展字段类型不受支持");
        }
        if ("enum".equals(type) && (field.options() == null || field.options().isEmpty()
                || field.options().stream().anyMatch(option -> option == null
                || option.value() == null || option.value().isBlank()))) {
            throw serviceUnavailable("APPROVAL_CONFIG_INVALID", "审批枚举字段选项配置无效");
        }
    }

    private ApprovalEnginePort.TaskSnapshot requireCurrentTaskOrEmpty(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return null;
        }
        try {
            Optional<ApprovalEnginePort.TaskSnapshot> current =
                    approvalEngine.getCurrentTask(processInstanceId);
            if (current == null) {
                throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎未返回当前任务查询结果");
            }
            ApprovalEnginePort.TaskSnapshot snapshot = current.orElse(null);
            if (snapshot != null) {
                requireValidTaskSnapshot(snapshot);
            }
            return snapshot;
        } catch (ApiPermissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "当前审批任务查询暂不可用");
        }
    }

    private List<ApprovalEnginePort.HistorySnapshot> requireHistory(String processInstanceId) {
        try {
            List<ApprovalEnginePort.HistorySnapshot> history = approvalEngine.history(processInstanceId);
            if (history == null || history.stream().anyMatch(Objects::isNull)) {
                throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎返回了无效流程历史");
            }
            return history;
        } catch (ApiPermissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批流程历史暂不可用");
        }
    }

    private void requireValidTaskSnapshot(ApprovalEnginePort.TaskSnapshot task) {
        if (!isValidTaskSnapshot(task)) {
            throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎返回了无效任务数据");
        }
    }

    private boolean isValidTaskSnapshot(ApprovalEnginePort.TaskSnapshot task) {
        return task != null && task.id() != null && !task.id().isBlank()
                && task.processInstanceId() != null && !task.processInstanceId().isBlank();
    }

    private record ApprovedCachePolicy(boolean enabled, Integer days) {
    }
}
