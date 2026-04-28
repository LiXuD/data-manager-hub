package com.dataplatform.vendor.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.VendorInfo;
import com.dataplatform.vendor.service.VendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Result<VendorInfo>> getById(@PathVariable Long id) {
        VendorInfo vendor = vendorService.getById(id);
        if (vendor == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "厂商不存在"));
        }
        return ResponseEntity.ok(Result.success(vendor));
    }

    @PostMapping
    public ResponseEntity<Result<VendorInfo>> create(@RequestBody VendorInfo vendor) {
        // 校验必填字段
        if (vendor.getVendorCode() == null || vendor.getVendorCode().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "厂商代码不能为空"));
        }
        if (vendor.getVendorName() == null || vendor.getVendorName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "厂商名称不能为空"));
        }
        if (vendor.getVendorType() == null || vendor.getVendorType().trim().isEmpty()) {
            vendor.setVendorType("other"); // 默认类型
        }

        // 检查重复
        VendorInfo existing = vendorService.getByVendorCode(vendor.getVendorCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "厂商代码已存在"));
        }

        vendor.setId(null);
        vendor.setStatus("active");
        vendorService.save(vendor);
        return ResponseEntity.ok(Result.success(vendor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<VendorInfo>> update(@PathVariable Long id, @RequestBody VendorInfo vendor) {
        // 检查是否存在
        VendorInfo existing = vendorService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "厂商不存在"));
        }
        vendor.setId(id);
        vendorService.updateById(vendor);
        return ResponseEntity.ok(Result.success(vendorService.getById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        // 检查是否存在
        VendorInfo existing = vendorService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "厂商不存在"));
        }
        vendorService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        // 校验status有效性
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "状态不能为空"));
        }
        if (!status.equals("active") && !status.equals("inactive") && !status.equals("suspended")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值"));
        }

        // 检查是否存在
        VendorInfo existing = vendorService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "厂商不存在"));
        }

        VendorInfo vendor = new VendorInfo();
        vendor.setId(id);
        vendor.setStatus(status);
        vendorService.updateById(vendor);
        return ResponseEntity.ok(Result.success(null));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Result<Map<String, Object>>> testConnection(@PathVariable Long id) {
        // 检查是否存在
        VendorInfo existing = vendorService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "厂商不存在"));
        }
        // TODO: 实现厂商连通性测试
        return ResponseEntity.ok(Result.success(Map.of("success", true, "message", "连接正常")));
    }
}