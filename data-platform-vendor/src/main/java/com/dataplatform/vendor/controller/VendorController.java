package com.dataplatform.vendor.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.VendorInfo;
import com.dataplatform.vendor.service.VendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
        return Result.success(vendorService.getById(id));
    }
}