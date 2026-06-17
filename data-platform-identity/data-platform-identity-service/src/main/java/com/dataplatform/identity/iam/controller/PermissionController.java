package com.dataplatform.identity.iam.controller;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 身份租户域用户权限的 Permission Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/permission")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @GetMapping("/list")
    public PageResult<Permission> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        return permissionService.list(keyword, status, page, pageSize);
    }

    @GetMapping("/all")
    public ResponseEntity<Result<List<Permission>>> listAllActive() {
        return ResponseEntity.ok(Result.success(permissionService.listAllActive()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<Permission>> get(@PathVariable Long id) {
        Permission permission = permissionService.getById(id);
        if (permission == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "权限不存在"));
        }
        return ResponseEntity.ok(Result.success(permission));
    }

    @OperationLog(module = "权限管理", operation = "新增权限")
    @PostMapping
    public ResponseEntity<Result<Permission>> create(@RequestBody Permission permission) {
        permission.setId(null);
        permissionService.save(permission);
        return ResponseEntity.ok(Result.success(permission));
    }

    @OperationLog(module = "权限管理", operation = "更新权限")
    @PutMapping("/{id}")
    public ResponseEntity<Result<Permission>> update(@PathVariable Long id, @RequestBody Permission permission) {
        Permission existing = permissionService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "权限不存在"));
        }
        permission.setId(id);
        permissionService.updateById(permission);
        return ResponseEntity.ok(Result.success(permissionService.getById(id)));
    }

    @OperationLog(module = "权限管理", operation = "删除权限")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        Permission existing = permissionService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "权限不存在"));
        }
        permissionService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }
}
