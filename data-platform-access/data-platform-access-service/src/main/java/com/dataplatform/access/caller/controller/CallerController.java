package com.dataplatform.access.caller.controller;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.CallerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 访问域调用方的 Caller Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/caller")
public class CallerController {

    @Autowired
    private CallerService callerService;

    @GetMapping("/list")
    public PageResult<CallerInfo> list(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {
        Long tenantId = tenantScope();
        if (!isPlatformAdmin() && tenantId == null) {
            PageResult<CallerInfo> denied = new PageResult<>();
            denied.setCode(HttpStatus.FORBIDDEN.value());
            denied.setMessage("当前用户没有租户作用域");
            denied.setData(java.util.List.of());
            denied.setTotal(0L);
            denied.setPage(page);
            denied.setPageSize(pageSize);
            return denied;
        }
        return callerService.list(page, pageSize, keyword, status, tenantId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<CallerInfo>> getById(@PathVariable Long id) {
        CallerInfo caller = callerService.getById(id);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        if (!tenantAllowed(caller)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        return ResponseEntity.ok(Result.success(caller));
    }

    @OperationLog(module = "调用方管理", operation = "新增调用方")
    @PostMapping
    public ResponseEntity<Result<CallerInfo>> create(@RequestBody CallerInfo caller) {
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "调用方不能为空"));
        }
        // 校验必填字段
        if (caller.getCallerName() == null || caller.getCallerName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "调用方名称不能为空"));
        }
        if (caller.getCallerCode() == null || caller.getCallerCode().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "调用方代码不能为空"));
        }
        caller.setCallerCode(caller.getCallerCode().trim());
        caller.setCallerName(caller.getCallerName().trim());

        Long effectiveTenantId = tenantScope();
        if (effectiveTenantId == null && !isPlatformAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(403, "当前用户没有租户作用域"));
        }
        if (effectiveTenantId == null) {
            effectiveTenantId = caller.getTenantId();
        }
        if (effectiveTenantId == null || effectiveTenantId <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "租户不能为空"));
        }

        // 检查重复 (使用callerCode)
        CallerInfo existing = callerService.getByCode(caller.getCallerCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "调用方代码已存在"));
        }

        caller.setId(null);
        caller.setTenantId(effectiveTenantId);
        caller.setStatus(CommonStatus.ACTIVE);
        caller.setDeleted(false);
        caller.setCreatedBy(null);
        caller.setCreatedAt(null);
        caller.setUpdatedBy(null);
        caller.setUpdatedAt(null);
        if (!callerService.save(caller)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "调用方创建失败，请重试"));
        }
        return ResponseEntity.ok(Result.success(caller));
    }

    @OperationLog(module = "调用方管理", operation = "更新调用方")
    @PutMapping("/{id}")
    public ResponseEntity<Result<CallerInfo>> update(@PathVariable Long id, @RequestBody CallerInfo caller) {
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "请求体不能为空"));
        }
        // 检查是否存在
        CallerInfo existing = callerService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        if (!tenantAllowed(existing)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        if (caller.getCallerName() == null || caller.getCallerName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "调用方名称不能为空"));
        }
        caller.setCallerName(caller.getCallerName().trim());
        caller.setTenantId(existing.getTenantId());
        caller.setCallerCode(existing.getCallerCode());
        caller.setStatus(null);
        caller.setDeleted(null);
        caller.setCreatedBy(null);
        caller.setCreatedAt(null);
        caller.setUpdatedBy(null);
        caller.setUpdatedAt(null);
        caller.setId(id);
        if (!callerService.updateById(caller)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "调用方已被其他请求修改，请刷新后重试"));
        }
        return ResponseEntity.ok(Result.success(callerService.getById(id)));
    }

    @OperationLog(module = "调用方管理", operation = "删除调用方")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        // 检查是否存在
        CallerInfo existing = callerService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        if (!tenantAllowed(existing)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        if (!callerService.removeById(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "调用方删除失败，请重试"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "调用方管理", operation = "更新调用方状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (body == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "请求体不能为空"));
        }
        String status = body.get("status");
        CommonStatus statusEnum = CommonStatus.fromCode(status);
        if (statusEnum == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值，必须是active或inactive"));
        }

        CallerInfo existing = callerService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        if (!tenantAllowed(existing)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }

        CallerInfo caller = new CallerInfo();
        caller.setId(id);
        caller.setStatus(statusEnum);
        if (!callerService.updateById(caller)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "调用方状态已被其他请求修改，请刷新后重试"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    private boolean isPlatformAdmin() {
        return UserContext.hasPermission("system:admin");
    }

    private Long tenantScope() {
        return UserContext.getCurrentTenantId();
    }

    private boolean tenantAllowed(CallerInfo caller) {
        return isPlatformAdmin()
                || (tenantScope() != null && tenantScope().equals(caller.getTenantId()));
    }
}
