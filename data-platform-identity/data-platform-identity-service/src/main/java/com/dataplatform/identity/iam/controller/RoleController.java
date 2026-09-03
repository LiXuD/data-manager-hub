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
import java.util.Locale;
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
        if (role == null) {
            return badRequest("请求体不能为空");
        }
        role.setRoleCode(authorizationService.canonicalRoleCode(role.getRoleCode()));
        if (role.getRoleName() == null || role.getRoleName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "角色名称不能为空"));
        }
        role.setRoleName(role.getRoleName().trim());
        if (role.getDescription() != null) {
            role.setDescription(role.getDescription().trim());
        }

        Role existing = roleService.getByRoleCode(role.getRoleCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "角色代码已存在"));
        }

        role.setId(null);
        role.setStatus(CommonStatus.ACTIVE);
        role.setDeleted(false);
        role.setCreatedBy(null);
        role.setCreatedAt(null);
        role.setUpdatedBy(null);
        role.setUpdatedAt(null);
        if (!roleService.save(role)) {
            return conflict("角色创建失败，请重试");
        }
        return ResponseEntity.ok(Result.success(role));
    }

    @OperationLog(module = "角色管理", operation = "更新角色")
    @PutMapping("/{id}")
    public ResponseEntity<Result<Role>> update(@PathVariable Long id, @RequestBody Role role) {
        authorizationService.requirePermission("role:edit");
        authorizationService.requirePlatformAdmin();
        if (role == null) {
            return badRequest("请求体不能为空");
        }
        Role existing = roleService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        if (role.getRoleCode() != null) {
            String requestedRoleCode = authorizationService.canonicalRoleCode(role.getRoleCode());
            if (!requestedRoleCode.equals(existing.getRoleCode())) {
                return badRequest("角色代码不可修改");
            }
        }
        if (role.getRoleName() == null || role.getRoleName().trim().isEmpty()) {
            return badRequest("角色名称不能为空");
        }
        role.setRoleCode(existing.getRoleCode());
        role.setRoleName(role.getRoleName().trim());
        if (role.getDescription() != null) {
            role.setDescription(role.getDescription().trim());
        }
        role.setStatus(existing.getStatus());
        role.setDeleted(null);
        role.setCreatedBy(null);
        role.setCreatedAt(null);
        role.setUpdatedBy(null);
        role.setUpdatedAt(null);
        role.setId(id);
        if (!roleService.updateById(role)) {
            return conflict("角色已被其他请求修改，请刷新后重试");
        }
        authorizationService.invalidateUsersWithRole(id);
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
        if (!roleService.removeById(id)) {
            return conflict("角色删除失败，请重试");
        }
        authorizationService.invalidateUsersWithRole(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "角色管理", operation = "更新角色状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        authorizationService.requirePermission("role:edit");
        authorizationService.requirePlatformAdmin();
        if (body == null) {
            return badRequest("请求体不能为空");
        }
        String status = body.get("status");

        CommonStatus statusEnum = CommonStatus.fromCode(
                status == null ? null : status.trim().toLowerCase(Locale.ROOT));
        if (statusEnum == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值，必须是active或inactive"));
        }

        Role existing = roleService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        if (statusEnum.equals(existing.getStatus())) {
            return ResponseEntity.ok(Result.success(null));
        }

        Role role = new Role();
        role.setId(id);
        role.setStatus(statusEnum);
        if (!roleService.updateById(role)) {
            return conflict("角色状态已被其他请求修改，请刷新后重试");
        }
        authorizationService.invalidateUsersWithRole(id);
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
        if (permissionIds == null || permissionIds.stream().anyMatch(java.util.Objects::isNull)) {
            return badRequest("权限ID列表不能为空且不能包含空值");
        }
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        List<Long> distinctPermissionIds = permissionIds.stream().distinct().toList();
        authorizationService.preparePermissionAssignment(id, distinctPermissionIds);
        rolePermissionService.assignPermissions(id, distinctPermissionIds);
        return ResponseEntity.ok(Result.success(null));
    }

    private <T> ResponseEntity<Result<T>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, message));
    }

    private <T> ResponseEntity<Result<T>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(409, message));
    }
}
