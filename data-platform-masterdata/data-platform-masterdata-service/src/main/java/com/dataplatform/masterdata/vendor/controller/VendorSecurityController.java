package com.dataplatform.masterdata.vendor.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityCapabilityDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityOrderReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityPreviewDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityPreviewReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecuritySaveReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityStepListDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityVersionDTO;
import com.dataplatform.masterdata.vendor.service.SecurityConfigConflictException;
import com.dataplatform.masterdata.vendor.service.VendorHealthService;
import com.dataplatform.masterdata.vendor.service.VendorSecurityService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vendor/config")
public class VendorSecurityController {

    private final VendorSecurityService securityService;
    private final VendorHealthService healthService;

    public VendorSecurityController(VendorSecurityService securityService, VendorHealthService healthService) {
        this.securityService = securityService;
        this.healthService = healthService;
    }

    @GetMapping("/security-capabilities")
    public Result<List<VendorSecurityCapabilityDTO>> capabilities() {
        if (!allowed("vendor:view")) {
            return Result.error(403, "没有厂商安全配置查看权限");
        }
        return Result.success(securityService.capabilities());
    }

    @GetMapping("/{configId}/security-steps")
    public Result<VendorSecurityStepListDTO> getSteps(@PathVariable Long configId) {
        if (!allowed("vendor:view")) {
            return Result.error(403, "没有厂商安全配置查看权限");
        }
        return Result.success(securityService.getSteps(configId));
    }

    @OperationLog(module = "厂商配置管理", operation = "保存接口安全流水线")
    @PutMapping("/{configId}/security-steps")
    public Result<VendorSecurityStepListDTO> saveSteps(@PathVariable Long configId,
                                                       @RequestBody VendorSecuritySaveReqDTO request) {
        if (!allowed("vendor:edit")) {
            return Result.error(403, "没有厂商安全配置编辑权限");
        }
        if (request == null) {
            return Result.error(400, "安全配置请求不能为空");
        }
        return Result.success(securityService.replaceSteps(configId, request.getVersion(), request.getSteps()));
    }

    @OperationLog(module = "厂商配置管理", operation = "接口安全步骤排序")
    @PutMapping("/{configId}/security-steps/order")
    public Result<VendorSecurityStepListDTO> reorder(@PathVariable Long configId,
                                                     @RequestBody VendorSecurityOrderReqDTO request) {
        if (!allowed("vendor:edit")) {
            return Result.error(403, "没有厂商安全配置编辑权限");
        }
        if (request == null) {
            return Result.error(400, "安全配置请求不能为空");
        }
        return Result.success(securityService.reorder(configId, request));
    }

    @PostMapping("/{configId}/security-preview")
    public Result<VendorSecurityPreviewDTO> preview(@PathVariable Long configId,
                                                    @RequestBody VendorSecurityPreviewReqDTO request) {
        if (!allowed("vendor:edit")) {
            return Result.error(403, "没有厂商安全配置编辑权限");
        }
        if (request == null) {
            return Result.error(400, "安全配置请求不能为空");
        }
        return Result.success(securityService.preview(configId, request));
    }

    @PostMapping("/{configId}/security-test")
    public Result<Map<String, Object>> test(@PathVariable Long configId) {
        if (!allowed("vendor:edit")) {
            return Result.error(403, "没有厂商安全配置编辑权限");
        }
        return Result.success(healthService.testConnection(configId));
    }

    @GetMapping("/{configId}/security-versions")
    public Result<List<VendorSecurityVersionDTO>> history(@PathVariable Long configId) {
        if (!allowed("vendor:view")) {
            return Result.error(403, "没有厂商安全配置查看权限");
        }
        return Result.success(securityService.history(configId));
    }

    @OperationLog(module = "厂商配置管理", operation = "回滚接口安全配置")
    @PostMapping("/{configId}/security-versions/{versionId}/rollback")
    public Result<VendorSecurityStepListDTO> rollback(@PathVariable Long configId,
                                                      @PathVariable Long versionId,
                                                      @RequestParam Integer version) {
        if (!allowed("vendor:edit")) {
            return Result.error(403, "没有厂商安全配置编辑权限");
        }
        return Result.success(securityService.rollback(configId, versionId, version));
    }

    private boolean allowed(String permission) {
        return UserContext.hasPermission(permission)
                || UserContext.hasPermission("system:admin");
    }

    @ExceptionHandler(SecurityConfigConflictException.class)
    public ResponseEntity<Result<Void>> handleConflict(SecurityConfigConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(409, exception.getMessage()));
    }
}
