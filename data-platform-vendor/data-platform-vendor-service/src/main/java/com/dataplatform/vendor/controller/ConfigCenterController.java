package com.dataplatform.vendor.controller;

import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.ConfigVersion;
import com.dataplatform.vendor.entity.VendorConfigExtended;
import com.dataplatform.vendor.service.VendorConfigExtendedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/config")
public class ConfigCenterController {

    private static final Set<String> VALID_STATUSES = Set.of(
        StatusConstants.ACTIVE, StatusConstants.INACTIVE, StatusConstants.PENDING
    );

    @Autowired
    private VendorConfigExtendedService configService;

    @GetMapping("/list")
    public PageResult<VendorConfigExtended> list(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return configService.list(vendorId, keyword, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<VendorConfigExtended>> get(@PathVariable Long id) {
        VendorConfigExtended config = configService.getById(id);
        if (config == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(config));
    }

    @PostMapping
    public ResponseEntity<Result<VendorConfigExtended>> create(@RequestBody VendorConfigExtended config) {
        if (config.getConfigKey() == null || config.getConfigKey().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "configKey不能为空"));
        }
        config.setId(null);
        config.setStatus(StatusConstants.ACTIVE);
        configService.save(config);
        return ResponseEntity.ok(Result.success(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<VendorConfigExtended>> update(@PathVariable Long id, @RequestBody VendorConfigExtended config) {
        VendorConfigExtended existing = configService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        config.setId(id);
        configService.updateById(config);
        return ResponseEntity.ok(Result.success(config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        if (!configService.removeById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/vendor/{vendorId}")
    public Result<List<VendorConfigExtended>> getByVendor(@PathVariable Long vendorId) {
        return Result.success(configService.getByVendor(vendorId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: " + VALID_STATUSES));
        }

        boolean updated = configService.lambdaUpdate()
                .eq(VendorConfigExtended::getId, id)
                .set(VendorConfigExtended::getStatus, status)
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
