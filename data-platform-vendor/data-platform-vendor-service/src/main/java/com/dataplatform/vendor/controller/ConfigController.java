package com.dataplatform.vendor.controller;

<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.VendorExtendedConfig;
import com.dataplatform.vendor.service.VendorExtendedConfigService;
========
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.ConfigVersion;
import com.dataplatform.vendor.entity.VendorConfigExtended;
import com.dataplatform.vendor.service.VendorConfigExtendedService;
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/config")
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
public class ConfigController {
    @Autowired
    private VendorExtendedConfigService extendedConfigService;
========
public class ConfigCenterController {
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java

    private static final Set<String> VALID_STATUSES = Set.of(
        StatusConstants.ACTIVE, StatusConstants.INACTIVE, StatusConstants.PENDING
    );

    @Autowired
    private VendorConfigExtendedService configService;

    @GetMapping("/list")
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
    public PageResult<VendorExtendedConfig> list(
========
    public PageResult<VendorConfigExtended> list(
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return extendedConfigService.list(vendorId, keyword, page, pageSize);
    }

    @GetMapping("/{id}")
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
    public ResponseEntity<Result<VendorExtendedConfig>> get(@PathVariable Long id) {
        VendorExtendedConfig config = extendedConfigService.getById(id);
========
    public ResponseEntity<Result<VendorConfigExtended>> get(@PathVariable Long id) {
        VendorConfigExtended config = configService.getById(id);
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
        if (config == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "配置管理", operation = "新增配置")
    @PostMapping
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
    public ResponseEntity<Result<VendorExtendedConfig>> create(@RequestBody VendorExtendedConfig config) {
========
    public ResponseEntity<Result<VendorConfigExtended>> create(@RequestBody VendorConfigExtended config) {
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
        if (config.getConfigKey() == null || config.getConfigKey().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "configKey不能为空"));
        }
        config.setId(null);
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
        config.setStatus("active");
        extendedConfigService.save(config);
========
        config.setStatus(StatusConstants.ACTIVE);
        configService.save(config);
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "配置管理", operation = "更新配置")
    @PutMapping("/{id}")
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
    public ResponseEntity<Result<VendorExtendedConfig>> update(@PathVariable Long id, @RequestBody VendorExtendedConfig config) {
        VendorExtendedConfig existing = extendedConfigService.getById(id);
========
    public ResponseEntity<Result<VendorConfigExtended>> update(@PathVariable Long id, @RequestBody VendorConfigExtended config) {
        VendorConfigExtended existing = configService.getById(id);
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        config.setId(id);
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
        extendedConfigService.updateById(config);
        return ResponseEntity.ok(Result.success(extendedConfigService.getById(id)));
========
        configService.updateById(config);
        return ResponseEntity.ok(Result.success(config));
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
    }

    @OperationLog(module = "配置管理", operation = "删除配置")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
        VendorExtendedConfig existing = extendedConfigService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        extendedConfigService.removeById(id);
========
        if (!configService.removeById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/vendor/{vendorId}")
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
    public Result<List<VendorExtendedConfig>> getByVendor(@PathVariable Long vendorId) {
        return Result.success(extendedConfigService.getByVendor(vendorId));
========
    public Result<List<VendorConfigExtended>> getByVendor(@PathVariable Long vendorId) {
        return Result.success(configService.getByVendor(vendorId));
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
    }

    @OperationLog(module = "配置管理", operation = "更新配置状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: " + VALID_STATUSES));
        }

<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigController.java
        VendorExtendedConfig existing = extendedConfigService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }

        VendorExtendedConfig config = new VendorExtendedConfig();
        config.setId(id);
        config.setStatus(status);
        extendedConfigService.updateById(config);
========
        boolean updated = configService.lambdaUpdate()
                .eq(VendorConfigExtended::getId, id)
                .set(VendorConfigExtended::getStatus, status)
                .update();

        if (!updated) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/controller/ConfigCenterController.java
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
