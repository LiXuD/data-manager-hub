package com.dataplatform.identity.iam.controller;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.security.IamAuthorizationService;
import com.dataplatform.identity.iam.service.RolePermissionService;
import com.dataplatform.identity.iam.service.RoleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 身份租户域用户权限的 Role Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/role")
public class RoleController {
    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;
    private final IamAuthorizationService authorizationService;

    public RoleController(
            RoleService roleService,
            RolePermissionService rolePermissionService,
            IamAuthorizationService authorizationService) {
        this.roleService = roleService;
        this.rolePermissionService = rolePermissionService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/list")
    public PageResult<Role> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        authorizationService.requirePermission("role:view");
        return roleService.list(keyword, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<Role>> get(@PathVariable Long id) {
        authorizationService.requirePermission("role:view");
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        return ResponseEntity.ok(Result.success(role));
    }

    @OperationLog(module = "角色管理", operation = "新增角色")
    @PostMapping
    public ResponseEntity<Result<Role>> create(@RequestBody Role role) {
        authorizationService.requirePermission("role:add");
        authorizationService.requirePlatformAdmin();
        role.setRoleCode(authorizationService.canonicalRoleCode(role.getRoleCode()));
        if (role.getRoleName() == null || role.getRoleName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "角色名称不能为空"));
        }

        Role existing = roleService.getByRoleCode(role.getRoleCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "角色代码已存在"));
        }

        role.setId(null);
        role.setStatus(CommonStatus.ACTIVE);
        roleService.save(role);
        return ResponseEntity.ok(Result.success(role));
    }

    @OperationLog(module = "角色管理", operation = "更新角色")
    @PutMapping("/{id}")
    public ResponseEntity<Result<Role>> update(@PathVariable Long id, @RequestBody Role role) {
        authorizationService.requirePermission("role:edit");
        authorizationService.requirePlatformAdmin();
        Role existing = roleService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        if (role.getRoleCode() != null) {
            role.setRoleCode(authorizationService.canonicalRoleCode(role.getRoleCode()));
            Role duplicate = roleService.getByRoleCode(role.getRoleCode());
            if (duplicate != null && !id.equals(duplicate.getId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.error(409, "角色代码已存在"));
            }
        }
        authorizationService.invalidateUsersWithRole(id);
        role.setId(id);
        roleService.updateById(role);
        return ResponseEntity.ok(Result.success(roleService.getById(id)));
    }

    @OperationLog(module = "角色管理", operation = "删除角色")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        authorizationService.requirePermission("role:delete");
        authorizationService.requirePlatformAdmin();
        Role existing = roleService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        authorizationService.requireRoleNotAssigned(id);
        roleService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "角色管理", operation = "更新角色状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        authorizationService.requirePermission("role:edit");
        authorizationService.requirePlatformAdmin();
        String status = body.get("status");

        CommonStatus statusEnum = CommonStatus.fromCode(status);
        if (statusEnum == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值，必须是active或inactive"));
        }

        Role existing = roleService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }

        Role role = new Role();
        role.setId(id);
        role.setStatus(statusEnum);
        authorizationService.invalidateUsersWithRole(id);
        roleService.updateById(role);
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<Result<List<Permission>>> getRolePermissions(@PathVariable Long id) {
        authorizationService.requirePermission("role:view");
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        List<Permission> permissions = rolePermissionService.getPermissionsByRoleId(id);
        return ResponseEntity.ok(Result.success(permissions));
    }

    @GetMapping("/{id}/permissionIds")
    public ResponseEntity<Result<List<Long>>> getRolePermissionIds(@PathVariable Long id) {
        authorizationService.requirePermission("role:view");
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        List<Long> permissionIds = rolePermissionService.getPermissionIdsByRoleId(id);
        return ResponseEntity.ok(Result.success(permissionIds));
    }

    @OperationLog(module = "角色管理", operation = "分配权限")
    @PostMapping("/{id}/permissions")
    public ResponseEntity<Result<Void>> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        authorizationService.requirePermission("role:edit");
        authorizationService.requirePlatformAdmin();
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        authorizationService.preparePermissionAssignment(id, permissionIds);
        rolePermissionService.assignPermissions(id, permissionIds);
        return ResponseEntity.ok(Result.success(null));
    }
}
