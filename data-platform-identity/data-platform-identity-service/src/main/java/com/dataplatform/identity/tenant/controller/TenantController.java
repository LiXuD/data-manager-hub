package com.dataplatform.identity.tenant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.identity.tenant.entity.TenantInfo;
import com.dataplatform.identity.tenant.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
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
    public ResponseEntity<Result<TenantInfo>> getById(@PathVariable(name = "id") Long id) {
        TenantInfo tenant = tenantService.getById(id);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "租户不存在"));
        }
        return ResponseEntity.ok(Result.success(tenant));
    }

    @OperationLog(module = "租户管理", operation = "新增租户")
    @PostMapping
    public ResponseEntity<Result<TenantInfo>> create(@RequestBody TenantInfo tenant) {
        // 校验必填字段
        if (tenant.getTenantCode() == null || tenant.getTenantCode().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "租户代码不能为空"));
        }
        if (tenant.getTenantName() == null || tenant.getTenantName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "租户名称不能为空"));
        }

        // 检查重复
        TenantInfo existing = tenantService.getByTenantCode(tenant.getTenantCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "租户代码已存在"));
        }

        tenant.setId(null);
        tenant.setStatus("active");
        tenantService.save(tenant);
        return ResponseEntity.ok(Result.success(tenant));
    }

    @OperationLog(module = "租户管理", operation = "更新租户")
    @PutMapping("/{id}")
    public ResponseEntity<Result<TenantInfo>> update(@PathVariable(name = "id") Long id, @RequestBody TenantInfo tenant) {
        // 检查是否存在
        TenantInfo existing = tenantService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "租户不存在"));
        }
        tenant.setId(id);
        tenantService.updateById(tenant);
        return ResponseEntity.ok(Result.success(tenantService.getById(id)));
    }

    @OperationLog(module = "租户管理", operation = "删除租户")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable(name = "id") Long id) {
        // 检查是否存在
        TenantInfo existing = tenantService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "租户不存在"));
        }
        tenantService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "租户管理", operation = "更新租户状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable(name = "id") Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        // 校验status有效性
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "状态不能为空"));
        }

        List<String> validStatuses = Arrays.asList("active", "inactive", "suspended");
        if (!validStatuses.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值"));
        }

        // 检查是否存在
        TenantInfo existing = tenantService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "租户不存在"));
        }

        TenantInfo tenant = new TenantInfo();
        tenant.setId(id);
        tenant.setStatus(status);
        tenantService.updateById(tenant);
        return ResponseEntity.ok(Result.success(null));
    }
}