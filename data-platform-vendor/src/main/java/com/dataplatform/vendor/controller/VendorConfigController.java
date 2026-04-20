package com.dataplatform.vendor.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.VendorConfig;
import com.dataplatform.vendor.service.VendorConfigService;
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vendor/config")
public class VendorConfigController {
    
    @Autowired
    private VendorConfigService vendorConfigService;
    
    @GetMapping("/list")
    public Result<List<VendorConfig>> list() {
        return Result.success(vendorConfigService.list());
    }
    
    @GetMapping("/{id}")
    public Result<VendorConfig> getById(@PathVariable Long id) {
        return Result.success(vendorConfigService.getById(id));
    }
    
    @PostMapping
    public Result<Void> create(@RequestBody VendorConfig config) {
        vendorConfigService.save(config);
        return Result.success(null);
    }
    
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody VendorConfig config) {
        config.setId(id);
        vendorConfigService.updateById(config);
        return Result.success(null);
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        vendorConfigService.removeById(id);
        return Result.success(null);
    }
}