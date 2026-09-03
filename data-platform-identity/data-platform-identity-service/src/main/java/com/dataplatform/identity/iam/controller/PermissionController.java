package com.dataplatform.identity.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.security.IamAuthorizationService;
import com.dataplatform.identity.iam.service.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * 身份租户域用户权限的 Permission Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/permission")
public class PermissionController {

    private final PermissionService permissionService;
    private final IamAuthorizationService authorizationService;

    public PermissionController(
            PermissionService permissionService,
            IamAuthorizationService authorizationService) {
        this.permissionService = permissionService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/list")
    public PageResult<Permission> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        authorizationService.requirePermission("permission:view");
        return permissionService.list(keyword, status, page, pageSize);
    }

    @GetMapping("/all")
    public ResponseEntity<Result<List<Permission>>> listAllActive() {
        authorizationService.requirePermission("permission:view");
        return ResponseEntity.ok(Result.success(permissionService.listAllActive()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<Permission>> get(@PathVariable Long id) {
        authorizationService.requirePermission("permission:view");
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
        authorizationService.requirePermission("permission:add");
        authorizationService.requirePlatformAdmin();
        if (permission == null) {
            return badRequest("请求体不能为空");
        }
        permission.setPermissionCode(authorizationService.canonicalPermissionCode(
                permission.getPermissionCode()));
        if (permission.getPermissionName() == null || permission.getPermissionName().trim().isEmpty()) {
            return badRequest("权限名称不能为空");
        }
        permission.setPermissionName(permission.getPermissionName().trim());
        String resourceType = normalizeResourceType(permission.getResourceType());
        if (resourceType == null) {
            return badRequest("资源类型必须是page、button或api");
        }
        permission.setResourceType(resourceType);
        if (permission.getResourcePath() != null) {
            permission.setResourcePath(permission.getResourcePath().trim());
        }
        Permission existing = permissionService.getOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermissionCode, permission.getPermissionCode())
                .eq(Permission::getDeleted, false));
        if (existing != null) {
            return conflict("权限代码已存在");
        }
        permission.setId(null);
        permission.setStatus("active");
        permission.setDeleted(false);
        permission.setCreatedBy(null);
        permission.setCreatedAt(null);
        permission.setUpdatedBy(null);
        permission.setUpdatedAt(null);
        if (!permissionService.save(permission)) {
            return conflict("权限创建失败，请重试");
        }
        return ResponseEntity.ok(Result.success(permission));
    }

    @OperationLog(module = "权限管理", operation = "更新权限")
    @PutMapping("/{id}")
    public ResponseEntity<Result<Permission>> update(@PathVariable Long id, @RequestBody Permission permission) {
        authorizationService.requirePermission("permission:edit");
        authorizationService.requirePlatformAdmin();
        if (permission == null) {
            return badRequest("请求体不能为空");
        }
        Permission existing = permissionService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "权限不存在"));
        }
        if (permission.getPermissionCode() != null) {
            String requestedCode = authorizationService.canonicalPermissionCode(
                    permission.getPermissionCode());
            if (!requestedCode.equals(existing.getPermissionCode())) {
                return badRequest("权限代码不可修改");
            }
        }
        if (permission.getPermissionName() == null || permission.getPermissionName().trim().isEmpty()) {
            return badRequest("权限名称不能为空");
        }
        permission.setPermissionCode(existing.getPermissionCode());
        permission.setPermissionName(permission.getPermissionName().trim());
        if (permission.getResourceType() != null && !permission.getResourceType().trim().isEmpty()) {
            String resourceType = normalizeResourceType(permission.getResourceType());
            if (resourceType == null) {
                return badRequest("资源类型必须是page、button或api");
            }
            permission.setResourceType(resourceType);
        } else {
            permission.setResourceType(existing.getResourceType());
        }
        if (permission.getResourcePath() != null) {
            permission.setResourcePath(permission.getResourcePath().trim());
        }
        permission.setStatus(existing.getStatus());
        permission.setDeleted(null);
        permission.setCreatedBy(null);
        permission.setCreatedAt(null);
        permission.setUpdatedBy(null);
        permission.setId(id);
        permission.setUpdatedAt(null);
        if (!permissionService.updateById(permission)) {
            return conflict("权限已被其他请求修改，请刷新后重试");
        }
        authorizationService.invalidateUsersWithPermission(id);
        return ResponseEntity.ok(Result.success(permissionService.getById(id)));
    }

    @OperationLog(module = "权限管理", operation = "删除权限")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        authorizationService.requirePermission("permission:delete");
        authorizationService.requirePlatformAdmin();
        Permission existing = permissionService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "权限不存在"));
        }
        if (!permissionService.removeById(id)) {
            return conflict("权限删除失败，请重试");
        }
        authorizationService.invalidateUsersWithPermission(id);
        return ResponseEntity.ok(Result.success(null));
    }

    private String normalizeResourceType(String resourceType) {
        if (resourceType == null || resourceType.trim().isEmpty()) {
            return "button";
        }
        String normalized = resourceType.trim().toLowerCase(Locale.ROOT);
        return List.of("page", "button", "api").contains(normalized) ? normalized : null;
    }

    private <T> ResponseEntity<Result<T>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, message));
    }

    private <T> ResponseEntity<Result<T>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(409, message));
    }
}
