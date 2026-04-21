package com.dataplatform.tenant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.tenant.entity.TenantInfo;
import com.dataplatform.tenant.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/tenant")
public class TenantController {

    @Autowired
    private TenantService tenantService;

    @GetMapping("/list")
    public PageResult<TenantInfo> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {

        Page<TenantInfo> pageResult = tenantService.listPage(page, pageSize, keyword, status);

        PageResult<TenantInfo> result = new PageResult<>();
        result.setCode(0);
        result.setMessage("success");
        result.setData(pageResult.getRecords());
        result.setTotal(pageResult.getTotal());
        result.setPage(page);
        result.setPageSize(pageSize);

        return result;
    }

    @GetMapping("/{id}")
    public Result<TenantInfo> getById(@PathVariable(name = "id") Long id) {
        TenantInfo tenant = tenantService.getById(id);
        if (tenant == null) {
            return Result.fail(404, "租户不存在");
        }
        return Result.success(tenant);
    }

    @PostMapping
    public Result<TenantInfo> create(@RequestBody TenantInfo tenant) {
        tenant.setId(null);
        tenant.setStatus("active");
        tenantService.save(tenant);
        return Result.success(tenant);
    }

    @PutMapping("/{id}")
    public Result<TenantInfo> update(@PathVariable(name = "id") Long id, @RequestBody TenantInfo tenant) {
        tenant.setId(id);
        tenantService.updateById(tenant);
        return Result.success(tenantService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable(name = "id") Long id) {
        tenantService.removeById(id);
        return Result.success(null);
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable(name = "id") Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        TenantInfo tenant = new TenantInfo();
        tenant.setId(id);
        tenant.setStatus(status);
        tenantService.updateById(tenant);
        return Result.success(null);
    }
}
