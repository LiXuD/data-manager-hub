package com.dataplatform.masterdata.vendor.controller;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig;
import com.dataplatform.masterdata.vendor.service.VendorExtendedConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        return extendedConfigService.list(vendorId, keyword, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<VendorExtendedConfig>> get(@PathVariable Long id) {
        VendorExtendedConfig config = extendedConfigService.getById(id);
        if (config == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "厂商扩展配置管理", operation = "新增扩展配置")
    @PostMapping
    public ResponseEntity<Result<VendorExtendedConfig>> create(@RequestBody VendorExtendedConfig config) {
        if (config.getConfigKey() == null || config.getConfigKey().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "configKey不能为空"));
        }
        config.setId(null);
        config.setStatus("active");
        extendedConfigService.save(config);
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "厂商扩展配置管理", operation = "更新扩展配置")
    @PutMapping("/{id}")
    public ResponseEntity<Result<VendorExtendedConfig>> update(@PathVariable Long id, @RequestBody VendorExtendedConfig config) {
        VendorExtendedConfig existing = extendedConfigService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        config.setId(id);
        extendedConfigService.updateById(config);
        return ResponseEntity.ok(Result.success(extendedConfigService.getById(id)));
    }

    @OperationLog(module = "厂商扩展配置管理", operation = "删除扩展配置")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        VendorExtendedConfig existing = extendedConfigService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        extendedConfigService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/vendor/{vendorId}")
    public Result<List<VendorExtendedConfig>> getByVendor(@PathVariable Long vendorId) {
        return Result.success(extendedConfigService.getByVendor(vendorId));
    }

    @OperationLog(module = "厂商扩展配置管理", operation = "更新扩展配置状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: " + VALID_STATUSES));
        }

        VendorExtendedConfig existing = extendedConfigService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }

        VendorExtendedConfig config = new VendorExtendedConfig();
        config.setId(id);
        config.setStatus(status);
        extendedConfigService.updateById(config);
        return ResponseEntity.ok(Result.success(null));
    }
}
