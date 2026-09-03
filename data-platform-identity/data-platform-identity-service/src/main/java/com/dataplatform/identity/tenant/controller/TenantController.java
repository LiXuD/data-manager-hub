package com.dataplatform.identity.tenant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.identity.iam.security.IamAuthorizationService;
import com.dataplatform.identity.tenant.entity.TenantInfo;
import com.dataplatform.identity.tenant.service.TenantService;
import com.dataplatform.common.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 身份租户域租户的 Tenant Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/tenant")
public class TenantController {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private IamAuthorizationService authorizationService;

    @GetMapping("/list")
    public PageResult<TenantInfo> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {

        authorizationService.requirePermission("tenant:view");
        if (!hasTenantScope()) {
            return deniedPage(page, pageSize);
        }
        Page<TenantInfo> pageResult = tenantService.listPage(page, pageSize, keyword, status, tenantScope());

        PageResult<TenantInfo> result = new PageResult<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(pageResult.getRecords());
        result.setTotal(pageResult.getTotal());
        result.setPage(page);
        result.setPageSize(pageSize);

        return result;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<TenantInfo>> getById(@PathVariable(name = "id") Long id) {
        authorizationService.requirePermission("tenant:view");
        TenantInfo tenant = tenantService.getById(id);
        if (tenant == null || !tenantAllowed(tenant)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "租户不存在"));
        }
        return ResponseEntity.ok(Result.success(tenant));
    }

    @OperationLog(module = "租户管理", operation = "新增租户")
    @PostMapping
    public ResponseEntity<Result<TenantInfo>> create(@RequestBody TenantInfo tenant) {
        authorizationService.requirePermission("tenant:add");
        if (!isPlatformAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(403, "只有平台管理员可以创建租户"));
        }
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "请求体不能为空"));
        }
        // 校验必填字段
        if (tenant.getTenantCode() == null || tenant.getTenantCode().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "租户代码不能为空"));
        }
        if (tenant.getTenantName() == null || tenant.getTenantName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "租户名称不能为空"));
        }
        tenant.setTenantCode(tenant.getTenantCode().trim());
        tenant.setTenantName(tenant.getTenantName().trim());

        // 检查重复
        TenantInfo existing = tenantService.getByTenantCode(tenant.getTenantCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "租户代码已存在"));
        }

        tenant.setId(null);
        tenant.setStatus("active");
        tenant.setDeleted(false);
        if (!tenantService.save(tenant)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "租户创建失败，请重试"));
        }
        return ResponseEntity.ok(Result.success(tenant));
    }

    @OperationLog(module = "租户管理", operation = "更新租户")
    @PutMapping("/{id}")
    public ResponseEntity<Result<TenantInfo>> update(@PathVariable(name = "id") Long id, @RequestBody TenantInfo tenant) {
        authorizationService.requirePermission("tenant:edit");
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "请求体不能为空"));
        }
        // 检查是否存在
        TenantInfo existing = tenantService.getById(id);
        if (existing == null || !tenantAllowed(existing)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "租户不存在"));
        }
        tenant.setId(id);
        tenant.setTenantCode(existing.getTenantCode());
        tenant.setStatus(existing.getStatus());
        tenant.setDeleted(null);
        tenant.setCreatedBy(null);
        tenant.setCreatedAt(null);
        tenant.setUpdatedAt(null);
        tenant.setUpdatedBy(null);
        if (!tenantService.updateById(tenant)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "租户已被其他请求修改，请刷新后重试"));
        }
        return ResponseEntity.ok(Result.success(tenantService.getById(id)));
    }

    @OperationLog(module = "租户管理", operation = "删除租户")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable(name = "id") Long id) {
        authorizationService.requirePermission("tenant:delete");
        if (!isPlatformAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(403, "只有平台管理员可以删除租户"));
        }
        // 检查是否存在
        TenantInfo existing = tenantService.getById(id);
        if (existing == null || !tenantAllowed(existing)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "租户不存在"));
        }
        if (!tenantService.removeById(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "租户删除失败，请重试"));
        }
        authorizationService.invalidateUsersInTenant(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "租户管理", operation = "更新租户状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable(name = "id") Long id, @RequestBody Map<String, String> body) {
        authorizationService.requirePermission("tenant:edit");
        if (body == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "请求体不能为空"));
        }
        String status = body.get("status");

        // 校验status有效性
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "状态不能为空"));
        }

        String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
        List<String> validStatuses = Arrays.asList("active", "inactive", "suspended");
        if (!validStatuses.contains(normalizedStatus)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值"));
        }

        // 检查是否存在
        TenantInfo existing = tenantService.getById(id);
        if (existing == null || !tenantAllowed(existing)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "租户不存在"));
        }

        String currentStatus = existing.getStatus() == null
                ? "" : existing.getStatus().trim().toLowerCase(Locale.ROOT);
        if ("suspended".equals(currentStatus) && !"active".equals(normalizedStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "暂停租户只能通过恢复动作转为active"));
        }
        if (currentStatus.equals(normalizedStatus)) {
            return ResponseEntity.ok(Result.success(null));
        }

        TenantInfo tenant = new TenantInfo();
        tenant.setId(id);
        tenant.setStatus(normalizedStatus);
        if (!tenantService.updateById(tenant)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "租户状态已被其他请求修改，请刷新后重试"));
        }
        if (!"active".equals(normalizedStatus)) {
            authorizationService.invalidateUsersInTenant(id);
        }
        return ResponseEntity.ok(Result.success(null));
    }

    private boolean isPlatformAdmin() {
        return UserContext.hasPermission("system:admin");
    }

    private Long tenantScope() {
        return isPlatformAdmin() ? null : UserContext.getCurrentTenantId();
    }

    private boolean hasTenantScope() {
        return isPlatformAdmin() || tenantScope() != null;
    }

    private boolean tenantAllowed(TenantInfo tenant) {
        return isPlatformAdmin() || (tenantScope() != null && tenantScope().equals(tenant.getId()));
    }

    private PageResult<TenantInfo> deniedPage(int page, int pageSize) {
        PageResult<TenantInfo> denied = new PageResult<>();
        denied.setCode(HttpStatus.FORBIDDEN.value());
        denied.setMessage("当前用户没有租户作用域");
        denied.setData(List.of());
        denied.setTotal(0L);
        denied.setPage(page);
        denied.setPageSize(pageSize);
        return denied;
    }
}
