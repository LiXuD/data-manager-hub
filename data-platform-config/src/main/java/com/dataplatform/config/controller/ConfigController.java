package com.dataplatform.config.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.config.entity.VendorConfig;
import com.dataplatform.config.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/config")
public class ConfigController {
    @Autowired
    private ConfigService configService;

    @GetMapping("/list")
    public PageResult<VendorConfig> list(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return configService.list(vendorId, keyword, page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<VendorConfig> get(@PathVariable Long id) {
        VendorConfig config = configService.getById(id);
        if (config == null) {
            return Result.fail(404, "配置不存在");
        }
        return Result.success(config);
    }

    @PostMapping
    public Result<VendorConfig> create(@RequestBody VendorConfig config) {
        config.setId(null);
        config.setStatus("active");
        configService.save(config);
        return Result.success(config);
    }

    @PutMapping("/{id}")
    public Result<VendorConfig> update(@PathVariable Long id, @RequestBody VendorConfig config) {
        config.setId(id);
        configService.updateById(config);
        return Result.success(configService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        configService.removeById(id);
        return Result.success(null);
    }

    @GetMapping("/vendor/{vendorId}")
    public Result<List<VendorConfig>> getByVendor(@PathVariable Long vendorId) {
        return Result.success(configService.getByVendor(vendorId));
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        VendorConfig config = new VendorConfig();
        config.setId(id);
        config.setStatus(status);
        configService.updateById(config);
        return Result.success(null);
    }
}