package com.dataplatform.access.approval.controller;

import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.ApiKeyOptionResponse;
import com.dataplatform.access.approval.api.CallerOptionResponse;
import com.dataplatform.access.approval.api.EmergencyGrantRequest;
import com.dataplatform.access.approval.api.GrantResponse;
import com.dataplatform.access.approval.api.InterfaceOptionResponse;
import com.dataplatform.access.approval.api.RevokeGrantRequest;
import com.dataplatform.access.approval.service.ApiPermissionGrantService;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-permission")
public class ApiPermissionGrantController {

    private final ApiPermissionGrantService grantService;

    public ApiPermissionGrantController(ApiPermissionGrantService grantService) {
        this.grantService = grantService;
    }

    @GetMapping("/grants")
    public Result<List<GrantResponse>> grants(
            @RequestParam(required = false) String status) {
        requirePermission("api-permission:grant-view");
        return Result.success(grantService.list(tenantId(), status));
    }

    @OperationLog(module = "接口权限审批", operation = "撤销接口权限授权")
    @PostMapping("/grants/{id}/revoke")
    public Result<ApiKeyInterface> revoke(
            @PathVariable Long id,
            @RequestBody RevokeGrantRequest request) {
        requirePermission("api-permission:revoke");
        return Result.success(grantService.revoke(
                id,
                request == null ? null : request.reason(),
                userId(),
                username(),
                tenantId()));
    }

    @OperationLog(module = "接口权限审批", operation = "紧急开通接口权限")
    @PostMapping("/emergency-grants")
    public Result<List<ApiKeyInterface>> emergencyGrant(
            @RequestBody EmergencyGrantRequest request) {
        requirePermission("api-permission:emergency-grant");
        return Result.success(grantService.emergencyGrant(
                request, userId(), username(), tenantId()));
    }

    @GetMapping("/emergency-options/callers")
    public Result<List<CallerOptionResponse>> emergencyCallers() {
        requirePermission("api-permission:emergency-grant");
        return Result.success(grantService.emergencyCallers(tenantId()));
    }

    @GetMapping("/emergency-options/callers/{callerId}/api-keys")
    public Result<List<ApiKeyOptionResponse>> emergencyApiKeys(@PathVariable Long callerId) {
        requirePermission("api-permission:emergency-grant");
        return Result.success(grantService.emergencyApiKeys(callerId, tenantId()));
    }

    @GetMapping("/emergency-options/interfaces")
    public Result<List<InterfaceOptionResponse>> emergencyInterfaces(
            @RequestParam Long apiKeyId,
            @RequestParam(required = false) String keyword) {
        requirePermission("api-permission:emergency-grant");
        return Result.success(grantService.emergencyInterfaces(apiKeyId, keyword, tenantId()));
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
