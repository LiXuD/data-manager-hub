package com.dataplatform.access.approval.controller;

import com.dataplatform.access.approval.api.ApiKeyOptionResponse;
import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.ApplicationDetailResponse;
import com.dataplatform.access.approval.api.ApplicationUpsertRequest;
import com.dataplatform.access.approval.api.CallerOptionResponse;
import com.dataplatform.access.approval.api.InterfaceOptionResponse;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.service.ApiPermissionApplicationService;
import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.util.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-permission")
public class ApiPermissionApplicationController {

    private final ApiPermissionApplicationService applicationService;

    public ApiPermissionApplicationController(
            ApiPermissionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/applications")
    public PageResult<ApiPermissionApplication> applications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        requirePermission("api-permission:view");
        boolean tenantScope = "tenant".equalsIgnoreCase(scope);
        if (tenantScope) {
            requirePermission("api-permission:approve");
        }
        return applicationService.list(
                userId(), tenantId(), tenantScope, status, page, pageSize);
    }

    @GetMapping("/applications/{id}")
    public Result<ApplicationDetailResponse> application(@PathVariable Long id) {
        requirePermission("api-permission:view");
        boolean tenantScope = UserContext.hasPermission("api-permission:approve");
        applicationService.requireVisibleApplication(
                id, userId(), tenantId(), tenantScope);
        return Result.success(applicationService.detail(id));
    }

    @OperationLog(module = "接口权限审批", operation = "创建接口权限申请草稿")
    @PostMapping("/applications")
    public Result<ApiPermissionApplication> create(
            @RequestBody ApplicationUpsertRequest request) {
        requirePermission("api-permission:apply");
        return Result.success(applicationService.createDraft(
                request, userId(), username(), tenantId()));
    }

    @OperationLog(module = "接口权限审批", operation = "编辑接口权限申请草稿")
    @PutMapping("/applications/{id}")
    public Result<ApiPermissionApplication> update(
            @PathVariable Long id,
            @RequestBody ApplicationUpsertRequest request) {
        requirePermission("api-permission:apply");
        return Result.success(applicationService.updateDraft(
                id, request, userId(), username(), tenantId()));
    }

    @OperationLog(module = "接口权限审批", operation = "提交接口权限申请")
    @PostMapping("/applications/{id}/submit")
    public Result<ApiPermissionApplication> submit(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requirePermission("api-permission:apply");
        return Result.success(applicationService.submit(
                id, idempotencyKey, userId(), username(), tenantId()));
    }

    @OperationLog(module = "接口权限审批", operation = "取消接口权限申请")
    @PostMapping("/applications/{id}/cancel")
    public Result<ApiPermissionApplication> cancel(@PathVariable Long id) {
        requirePermission("api-permission:apply");
        return Result.success(applicationService.cancel(id, userId(), username()));
    }

    @OperationLog(module = "接口权限审批", operation = "复制接口权限申请")
    @PostMapping("/applications/{id}/copy")
    public Result<ApiPermissionApplication> copy(@PathVariable Long id) {
        requirePermission("api-permission:apply");
        return Result.success(applicationService.copy(
                id, userId(), username(), tenantId()));
    }

    @GetMapping("/eligible-callers")
    public Result<List<CallerOptionResponse>> eligibleCallers() {
        requirePermission("api-permission:apply");
        return Result.success(applicationService.eligibleCallers(userId(), tenantId()));
    }

    @GetMapping("/callers/{callerId}/api-keys")
    public Result<List<ApiKeyOptionResponse>> apiKeys(@PathVariable Long callerId) {
        requirePermission("api-permission:apply");
        return Result.success(applicationService.callerApiKeys(
                callerId, userId(), tenantId()));
    }

    @GetMapping("/interface-options")
    public Result<List<InterfaceOptionResponse>> interfaceOptions(
            @RequestParam Long apiKeyId,
            @RequestParam(required = false) String keyword) {
        requirePermission("api-permission:apply");
        return Result.success(applicationService.interfaceOptions(
                apiKeyId, keyword, userId(), tenantId()));
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
