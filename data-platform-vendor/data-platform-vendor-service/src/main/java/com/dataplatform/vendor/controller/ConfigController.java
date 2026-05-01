package com.dataplatform.vendor.controller;

import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.ConfigVersion;
import com.dataplatform.vendor.entity.VendorExtendedConfig;
import com.dataplatform.vendor.service.VendorExtendedConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/config")
public class ConfigController {

    private static final Set<String> VALID_STATUSES = Set.of(
        StatusConstants.ACTIVE, StatusConstants.INACTIVE, StatusConstants.PENDING
    );

    @Autowired
    private VendorExtendedConfigService configService;

    @GetMapping("/list")
    public PageResult<VendorExtendedConfig> list(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return configService.list(vendorId, keyword, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<VendorExtendedConfig>> get(@PathVariable Long id) {
        VendorExtendedConfig config = configService.getById(id);
        if (config == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "配置管理", operation = "新增配置")
    @PostMapping
    public ResponseEntity<Result<VendorExtendedConfig>> create(@RequestBody VendorExtendedConfig config) {
        if (config.getConfigKey() == null || config.getConfigKey().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "configKey不能为空"));
        }
        config.setId(null);
        config.setStatus(StatusConstants.ACTIVE);
        configService.save(config);
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "配置管理", operation = "更新配置")
    @PutMapping("/{id}")
    public ResponseEntity<Result<VendorExtendedConfig>> update(@PathVariable Long id, @RequestBody VendorExtendedConfig config) {
        config.setId(id);
        boolean updated = configService.updateById(config);
        if (!updated) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "配置管理", operation = "删除配置")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        if (!configService.removeById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/vendor/{vendorId}")
    public Result<List<VendorExtendedConfig>> getByVendor(@PathVariable Long vendorId) {
        return Result.success(configService.getByVendor(vendorId));
    }

    @OperationLog(module = "配置管理", operation = "更新配置状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: " + VALID_STATUSES));
        }

        boolean updated = configService.lambdaUpdate()
                .eq(VendorExtendedConfig::getId, id)
                .set(VendorExtendedConfig::getStatus, status)
                .update();

        if (!updated) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/key/{configKey}")
    public Result<String> getConfigValue(@PathVariable String configKey) {
        return Result.success(configService.getConfig(configKey));
    }

    @PostMapping("/key/{configKey}/publish")
    public Result<Boolean> publishConfig(@PathVariable String configKey) {
        return Result.success(configService.publishConfig(configKey));
    }

    @GetMapping("/key/{configKey}/versions")
    public Result<List<ConfigVersion>> getVersionHistory(@PathVariable String configKey) {
        return Result.success(configService.getVersionHistory(configKey));
    }

    @PostMapping("/cache/clear")
    public Result<Void> clearCache() {
        configService.clearAllCache();
        return Result.success(null);
    }
}
