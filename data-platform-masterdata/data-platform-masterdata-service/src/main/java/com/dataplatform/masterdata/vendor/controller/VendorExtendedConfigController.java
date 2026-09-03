package com.dataplatform.masterdata.vendor.controller;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig;
import com.dataplatform.masterdata.vendor.service.VendorExtendedConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 主数据域厂商的 Vendor Extended Config Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/vendor/extended-config")
public class VendorExtendedConfigController {
    @Autowired
    private VendorExtendedConfigService extendedConfigService;

    private static final List<String> VALID_STATUSES = List.of("active", "inactive", "pending");

    @GetMapping("/list")
    public PageResult<VendorExtendedConfig> list(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        if (!canView()) {
            return forbiddenPage(page, pageSize);
        }
        return extendedConfigService.list(vendorId, keyword, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<VendorExtendedConfig>> get(@PathVariable Long id) {
        if (!canView()) {
            return forbidden();
        }
        VendorExtendedConfig config = extendedConfigService.getForDisplay(id);
        if (config == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "厂商扩展配置管理", operation = "新增扩展配置")
    @PostMapping
    public ResponseEntity<Result<VendorExtendedConfig>> create(@RequestBody VendorExtendedConfig config) {
        if (!canAdd()) {
            return forbidden();
        }
        if (config == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "配置请求不能为空"));
        }
        if (config.getConfigKey() == null || config.getConfigKey().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "configKey不能为空"));
        }
        config.setId(null);
        config.setConfigKey(config.getConfigKey().trim());
        config.setStatus("active");
        config.setIsActive(true);
        config.setUpdatedBy(null);
        config.setCreatedAt(null);
        config.setUpdatedAt(null);
        return ResponseEntity.ok(Result.success(extendedConfigService.saveSecure(config)));
    }

    @OperationLog(module = "厂商扩展配置管理", operation = "更新扩展配置")
    @PutMapping("/{id}")
    public ResponseEntity<Result<VendorExtendedConfig>> update(@PathVariable Long id, @RequestBody VendorExtendedConfig config) {
        if (!canEdit()) {
            return forbidden();
        }
        if (config == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "配置请求不能为空"));
        }
        VendorExtendedConfig existing = extendedConfigService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        VendorExtendedConfig updated = extendedConfigService.updateSecure(id, config);
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "配置更新失败"));
        }
        return ResponseEntity.ok(Result.success(updated));
    }

    @OperationLog(module = "厂商扩展配置管理", operation = "删除扩展配置")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        if (!canDelete()) {
            return forbidden();
        }
        VendorExtendedConfig existing = extendedConfigService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        if (!extendedConfigService.removeSecure(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "配置删除失败"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/vendor/{vendorId}")
    public Result<List<VendorExtendedConfig>> getByVendor(@PathVariable Long vendorId) {
        if (!canView()) {
            return Result.error(403, "没有厂商配置查看权限");
        }
        return Result.success(extendedConfigService.getByVendor(vendorId));
    }

    @OperationLog(module = "厂商扩展配置管理", operation = "更新扩展配置状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!canEdit()) {
            return forbidden();
        }
        if (body == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "状态请求不能为空"));
        }
        String status = body.get("status");
        status = status == null ? null : status.trim().toLowerCase(Locale.ROOT);

        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: " + VALID_STATUSES));
        }

        VendorExtendedConfig existing = extendedConfigService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }

        if (!extendedConfigService.updateStatusSecure(id, status)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "配置状态更新失败"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    private boolean canView() {
        return UserContext.hasPermission("vendor:view")
                || UserContext.hasPermission("system:admin");
    }

    private boolean canEdit() {
        return UserContext.hasPermission("vendor:edit")
                || UserContext.hasPermission("system:admin");
    }

    private boolean canAdd() {
        return UserContext.hasPermission("vendor:add")
                || UserContext.hasPermission("system:admin");
    }

    private boolean canDelete() {
        return UserContext.hasPermission("vendor:delete")
                || UserContext.hasPermission("system:admin");
    }

    private <T> ResponseEntity<Result<T>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(403, "没有厂商配置操作权限"));
    }

    private PageResult<VendorExtendedConfig> forbiddenPage(int page, int pageSize) {
        PageResult<VendorExtendedConfig> result = new PageResult<>();
        result.setCode(403);
        result.setMessage("没有厂商配置查看权限");
        result.setData(List.of());
        result.setTotal(0L);
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }
}
