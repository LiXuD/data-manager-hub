package com.dataplatform.identity.iam.security;

import cn.dev33.satoken.stp.StpUtil;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.service.RolePermissionService;
import com.dataplatform.identity.iam.service.RoleService;
import com.dataplatform.identity.iam.service.PermissionService;
import com.dataplatform.identity.iam.service.UserRoleService;
import com.dataplatform.identity.iam.service.UserService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IamAuthorizationService {

    public static final String PLATFORM_ADMIN_PERMISSION = "system:admin";
    private static final Pattern ROLE_CODE_PATTERN =
            Pattern.compile("[a-z][a-z0-9_:-]{0,99}");

    private final UserService userService;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final UserRoleService userRoleService;
    private final RolePermissionService rolePermissionService;

    public IamAuthorizationService(
            UserService userService,
            RoleService roleService,
            PermissionService permissionService,
            UserRoleService userRoleService,
            RolePermissionService rolePermissionService) {
        this.userService = userService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.userRoleService = userRoleService;
        this.rolePermissionService = rolePermissionService;
    }

    public void requirePermission(String permission) {
        if (!UserContext.hasPermission(permission)) {
            throw forbidden("PERMISSION_DENIED", "缺少权限：" + permission);
        }
    }

    public void requirePlatformAdmin() {
        requirePermission(PLATFORM_ADMIN_PERMISSION);
    }

    public boolean isPlatformAdmin() {
        return UserContext.hasPermission(PLATFORM_ADMIN_PERMISSION);
    }

    public User requireUserInScope(Long userId) {
        User user = userService.getById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new IamAuthorizationException(
                    HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        if (!isPlatformAdmin()
                && !Objects.equals(UserContext.getCurrentTenantId(), user.getTenantId())) {
            throw forbidden("USER_TENANT_DENIED", "不能访问其他租户用户");
        }
        return user;
    }

    public Long tenantFilter() {
        return isPlatformAdmin() ? null : UserContext.getCurrentTenantId();
    }

    public void prepareRoleAssignment(Long targetUserId, Collection<Long> roleIds) {
        forbidCurrentUserMutation(targetUserId, "SELF_ROLE_MUTATION_FORBIDDEN", "禁止修改自己的角色");
        requireUserInScope(targetUserId);
        List<Long> distinctRoleIds = roleIds == null
                ? List.of()
                : roleIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Role> roles = distinctRoleIds.isEmpty()
                ? List.of()
                : roleService.listByIds(distinctRoleIds);
        if (roles.size() != distinctRoleIds.size()
                || roles.stream().anyMatch(role -> Boolean.TRUE.equals(role.getDeleted())
                        || !CommonStatus.ACTIVE.equals(role.getStatus()))) {
            throw new IamAuthorizationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ROLE_ASSIGNMENT",
                    "只能分配存在且已启用的角色");
        }
        if (!isPlatformAdmin()) {
            Set<String> actorPermissions = new HashSet<>(UserContext.getCurrentPermissions());
            Set<String> grantedPermissions = roles.stream()
                    .flatMap(role -> rolePermissionService
                            .getPermissionsByRoleId(role.getId()).stream())
                    .filter(permission -> "active".equalsIgnoreCase(permission.getStatus()))
                    .map(Permission::getPermissionCode)
                    .collect(java.util.stream.Collectors.toSet());
            if (!actorPermissions.containsAll(grantedPermissions)) {
                throw forbidden(
                        "ROLE_PRIVILEGE_ESCALATION_FORBIDDEN",
                        "不能授予当前操作者未拥有的权限");
            }
        }
        invalidateUser(targetUserId);
    }

    public void forbidCurrentUserMutation(Long targetUserId, String code, String message) {
        if (Objects.equals(UserContext.getCurrentUserId(), targetUserId)) {
            throw forbidden(code, message);
        }
    }

    public String canonicalRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IamAuthorizationException(
                    HttpStatus.BAD_REQUEST, "INVALID_ROLE_CODE", "角色代码不能为空");
        }
        String canonical = roleCode.trim().toLowerCase(Locale.ROOT);
        if (!ROLE_CODE_PATTERN.matcher(canonical).matches()) {
            throw new IamAuthorizationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ROLE_CODE",
                    "角色代码必须以字母开头，只能包含小写字母、数字、下划线、冒号和短横线");
        }
        return canonical;
    }

    public String canonicalPermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IamAuthorizationException(
                    HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_CODE", "权限代码不能为空");
        }
        String canonical = permissionCode.trim().toLowerCase(Locale.ROOT);
        if (!ROLE_CODE_PATTERN.matcher(canonical).matches()) {
            throw new IamAuthorizationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PERMISSION_CODE",
                    "权限代码必须以字母开头，只能包含小写字母、数字、下划线、冒号和短横线");
        }
        return canonical;
    }

    public void preparePermissionAssignment(Long roleId, Collection<Long> permissionIds) {
        List<Long> distinctPermissionIds = permissionIds == null
                ? List.of()
                : permissionIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Permission> permissions = distinctPermissionIds.isEmpty()
                ? List.of()
                : permissionService.listByIds(distinctPermissionIds);
        if (permissions.size() != distinctPermissionIds.size()
                || permissions.stream().anyMatch(permission ->
                        !"active".equalsIgnoreCase(permission.getStatus())
                                || Boolean.TRUE.equals(permission.getDeleted()))) {
            throw new IamAuthorizationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PERMISSION_ASSIGNMENT",
                    "只能分配存在且已启用的权限");
        }
        invalidateUsersWithRole(roleId);
    }

    public void requireRoleNotAssigned(Long roleId) {
        if (!userRoleService.getUserIdsByRoleId(roleId).isEmpty()) {
            throw new IamAuthorizationException(
                    HttpStatus.CONFLICT,
                    "ROLE_IN_USE",
                    "角色仍有关联用户，不能删除");
        }
    }

    public void invalidateUser(Long userId) {
        if (userId != null) {
            StpUtil.logout(userId);
        }
    }

    public void invalidateUsersWithRole(Long roleId) {
        userRoleService.getUserIdsByRoleId(roleId).forEach(this::invalidateUser);
    }

    public void invalidateUsersWithPermission(Long permissionId) {
        rolePermissionService.getRoleIdsByPermissionId(permissionId)
                .forEach(this::invalidateUsersWithRole);
    }

    public void invalidateAllUsers() {
        userService.listUserIds().forEach(this::invalidateUser);
    }

    private IamAuthorizationException forbidden(String code, String message) {
        return new IamAuthorizationException(HttpStatus.FORBIDDEN, code, message);
    }
}
