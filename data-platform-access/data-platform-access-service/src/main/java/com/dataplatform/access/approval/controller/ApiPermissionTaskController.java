package com.dataplatform.access.approval.controller;

import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.ApprovalTaskDetailResponse;
import com.dataplatform.access.approval.api.ApprovalTaskResponse;
import com.dataplatform.access.approval.api.CompleteTaskRequest;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.engine.ApprovalEnginePort;
import com.dataplatform.access.approval.service.ApiPermissionTaskService;
import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-permission")
public class ApiPermissionTaskController {

    private final ApiPermissionTaskService taskService;

    public ApiPermissionTaskController(ApiPermissionTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/tasks")
    public Result<List<ApprovalTaskResponse>> tasks() {
        requirePermission("api-permission:approve");
        return Result.success(taskService.listTasks(userId(), tenantId()));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<ApprovalTaskDetailResponse> task(@PathVariable String taskId) {
        requirePermission("api-permission:approve");
        return Result.success(taskService.taskDetail(taskId, userId(), tenantId()));
    }

    @OperationLog(module = "接口权限审批", operation = "认领接口权限审批任务")
    @PostMapping("/tasks/{taskId}/claim")
    public Result<ApprovalTaskResponse> claim(@PathVariable String taskId) {
        requirePermission("api-permission:approve");
        return Result.success(taskService.claim(taskId, userId(), tenantId()));
    }

    @OperationLog(module = "接口权限审批", operation = "释放接口权限审批任务")
    @PostMapping("/tasks/{taskId}/unclaim")
    public Result<Void> unclaim(@PathVariable String taskId) {
        requirePermission("api-permission:approve");
        taskService.unclaim(taskId, userId(), tenantId());
        return Result.success();
    }

    @OperationLog(module = "接口权限审批", operation = "完成接口权限审批任务")
    @PostMapping("/tasks/{taskId}/complete")
    public Result<ApiPermissionApplication> complete(
            @PathVariable String taskId,
            @RequestBody CompleteTaskRequest request) {
        requirePermission("api-permission:approve");
        return Result.success(taskService.complete(
                taskId, request, userId(), username(), tenantId()));
    }

    @GetMapping("/applications/{id}/process-history")
    public Result<List<ApprovalEnginePort.HistorySnapshot>> processHistory(
            @PathVariable Long id) {
        boolean tenantScope = UserContext.hasPermission("api-permission:process-view")
                || UserContext.hasPermission("api-permission:approve");
        if (!tenantScope) {
            requirePermission("api-permission:view");
        }
        return Result.success(taskService.processHistory(
                id, userId(), tenantId(), tenantScope));
    }

    private void requirePermission(String permission) {
        if (!UserContext.hasPermission(permission)) {
            throw new ApiPermissionException(
                    HttpStatus.FORBIDDEN,
                    "PERMISSION_DENIED",
                    "缺少权限：" + permission);
        }
    }

    private Long userId() {
        return UserContext.getCurrentUserId();
    }

    private Long tenantId() {
        return UserContext.getCurrentTenantId();
    }

    private String username() {
        return UserContext.getCurrentUsername();
    }
}
