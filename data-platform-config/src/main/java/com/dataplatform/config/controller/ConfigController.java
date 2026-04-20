package com.dataplatform.config.controller;

import com.dataplatform.common.pojo.ApiResponse;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.config.entity.VendorConfig;
import com.dataplatform.config.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {
    @Autowired
    private ConfigService configService;

    @GetMapping("/list")
    public ApiResponse<PageResponse<VendorConfig>> list(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String configKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<VendorConfig> result = configService.list(vendorId, configKey, page, pageSize);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<VendorConfig> get(@PathVariable Long id) {
        return ApiResponse.success(configService.getById(id));
    }

    @PostMapping
    public ApiResponse<VendorConfig> create(@RequestBody VendorConfig config) {
        return ApiResponse.success(configService.create(config));
    }

    @PutMapping("/{id}")
    public ApiResponse<VendorConfig> update(@PathVariable Long id, @RequestBody VendorConfig config) {
        config.setId(id);
        return ApiResponse.success(configService.update(config));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/vendor/{vendorId}")
    public ApiResponse<List<VendorConfig>> getByVendor(@PathVariable Long vendorId) {
        return ApiResponse.success(configService.getByVendor(vendorId));
    }
}
