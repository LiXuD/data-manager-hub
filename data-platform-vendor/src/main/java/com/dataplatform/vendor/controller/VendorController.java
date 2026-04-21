package com.dataplatform.vendor.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.VendorInfo;
import com.dataplatform.vendor.service.VendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/vendor")
public class VendorController {

    @Autowired
    private VendorService vendorService;

    @GetMapping("/list")
    public PageResult<VendorInfo> list(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {
        return vendorService.list(page, pageSize, keyword, status);
    }

    @GetMapping("/{id}")
    public Result<VendorInfo> getById(@PathVariable Long id) {
        VendorInfo vendor = vendorService.getById(id);
        if (vendor == null) {
            return Result.fail(404, "厂商不存在");
        }
        return Result.success(vendor);
    }

    @PostMapping
    public Result<VendorInfo> create(@RequestBody VendorInfo vendor) {
        vendor.setId(null);
        vendor.setStatus("active");
        vendorService.save(vendor);
        return Result.success(vendor);
    }

    @PutMapping("/{id}")
    public Result<VendorInfo> update(@PathVariable Long id, @RequestBody VendorInfo vendor) {
        vendor.setId(id);
        vendorService.updateById(vendor);
        return Result.success(vendorService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        vendorService.removeById(id);
        return Result.success(null);
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        VendorInfo vendor = new VendorInfo();
        vendor.setId(id);
        vendor.setStatus(status);
        vendorService.updateById(vendor);
        return Result.success(null);
    }

    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testConnection(@PathVariable Long id) {
        // TODO: 实现厂商连通性测试
        return Result.success(Map.of("success", true, "message", "连接正常"));
    }
}