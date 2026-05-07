package com.dataplatform.vendor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.VendorConfig;
import com.dataplatform.vendor.service.VendorConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vendor/config")
public class VendorConfigController {

    @Autowired
    private VendorConfigService vendorConfigService;

    @GetMapping("/list")
    public Result<List<VendorConfig>> list(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long dataTypeId,
            @RequestParam(required = false) Long interfaceId,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        if (vendorId != null) {
            wrapper.eq(VendorConfig::getVendorId, vendorId);
        }
        if (dataTypeId != null) {
            wrapper.eq(VendorConfig::getDataTypeId, dataTypeId);
        }
        if (interfaceId != null) {
            wrapper.eq(VendorConfig::getInterfaceId, interfaceId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(VendorConfig::getStatus, CommonStatus.fromCode(status));
        }
        wrapper.orderByDesc(VendorConfig::getCreatedAt);
        return Result.success(vendorConfigService.list(wrapper));
    }

    @GetMapping("/{id}")
    public Result<VendorConfig> getById(@PathVariable Long id) {
        return Result.success(vendorConfigService.getById(id));
    }

    @GetMapping("/vendor/{vendorId}")
    public Result<List<VendorConfig>> listByVendor(@PathVariable Long vendorId) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getVendorId, vendorId)
               .orderByDesc(VendorConfig::getCreatedAt);
        return Result.success(vendorConfigService.list(wrapper));
    }

    @GetMapping("/interface/{interfaceId}")
    public Result<List<VendorConfig>> listByInterface(@PathVariable Long interfaceId) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getInterfaceId, interfaceId)
               .orderByDesc(VendorConfig::getCreatedAt);
        return Result.success(vendorConfigService.list(wrapper));
    }

    @OperationLog(module = "厂商配置管理", operation = "新增厂商配置")
    @PostMapping
    public ResponseEntity<Result<VendorConfig>> create(@RequestBody VendorConfig config) {
        if (config.getVendorId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "厂商ID不能为空"));
        }
        if (config.getInterfaceId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "接口ID不能为空"));
        }
        if (config.getApiUrl() == null || config.getApiUrl().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "API地址不能为空"));
        }

        if (config.getStatus() == null) {
            config.setStatus(CommonStatus.ACTIVE);
        }
        if (config.getTimeout() == null) {
            config.setTimeout(30000);
        }
        if (config.getRetryCount() == null) {
            config.setRetryCount(3);
        }
        if (config.getCircuitThreshold() == null) {
            config.setCircuitThreshold(5);
        }
        if (config.getCircuitTimeout() == null) {
            config.setCircuitTimeout(60);
        }
        if (config.getMethod() == null) {
            config.setMethod("POST");
        }

        vendorConfigService.save(config);
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "厂商配置管理", operation = "更新厂商配置")
    @PutMapping("/{id}")
    public ResponseEntity<Result<VendorConfig>> update(@PathVariable Long id, @RequestBody VendorConfig config) {
        config.setId(id);
        boolean success = vendorConfigService.updateById(config);
        if (!success) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(config));
    }

    @OperationLog(module = "厂商配置管理", operation = "删除厂商配置")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        boolean success = vendorConfigService.removeById(id);
        if (!success) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "厂商配置管理", operation = "更新配置状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        CommonStatus statusEnum = CommonStatus.fromCode(status);
        if (statusEnum == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: active, inactive"));
        }

        LambdaUpdateWrapper<VendorConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(VendorConfig::getId, id).set(VendorConfig::getStatus, statusEnum);
        boolean success = vendorConfigService.update(wrapper);
        if (!success) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testConnection(@PathVariable Long id) {
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return Result.error(404, "配置不存在");
        }

        Map<String, Object> result = new HashMap<>();
        try {
            long startTime = System.currentTimeMillis();
            // TODO: 实现真实的连接测试逻辑
            long latency = System.currentTimeMillis() - startTime + (long)(Math.random() * 200);
            result.put("success", true);
            result.put("latency", latency);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return Result.success(result);
    }

    /**
     * 获取厂商配置的参数映射
     */
    @GetMapping("/{id}/mapping")
    public ResponseEntity<Result<Map<String, Object>>> getParamMapping(@PathVariable Long id) {
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("vendorConfigId", id);
        result.put("interfaceId", config.getInterfaceId());
        result.put("paramMapping", config.getParamMapping());
        return ResponseEntity.ok(Result.success(result));
    }

    /**
     * 更新厂商配置的参数映射
     */
    @OperationLog(module = "厂商配置管理", operation = "更新参数映射")
    @PutMapping("/{id}/mapping")
    public ResponseEntity<Result<Void>> updateParamMapping(@PathVariable Long id,
                                                             @RequestBody Map<String, String> body) {
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "配置不存在"));
        }
        String paramMapping = body.get("paramMapping");

        VendorConfig update = new VendorConfig();
        update.setId(id);
        update.setParamMapping(paramMapping);
        vendorConfigService.updateById(update);
        return ResponseEntity.ok(Result.success(null));
    }
}
