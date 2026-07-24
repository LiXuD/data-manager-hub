package com.dataplatform.identity.iam.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.entity.UserRole;
import com.dataplatform.identity.iam.mapper.PermissionMapper;
import com.dataplatform.identity.iam.mapper.RoleMapper;
import com.dataplatform.identity.iam.mapper.UserRoleMapper;
import com.dataplatform.identity.iam.service.RolePermissionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthControllerAuthorizationTest {

    @Test
    @SuppressWarnings("unchecked")
    void loginAuthorizationUsesOnlyActiveRolesAndPermissions() {
        AuthController controller = new AuthController();
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RolePermissionService rolePermissionService = mock(RolePermissionService.class);
        PermissionMapper permissionMapper = mock(PermissionMapper.class);
        ReflectionTestUtils.setField(controller, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(controller, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(
                controller, "rolePermissionService", rolePermissionService);
        ReflectionTestUtils.setField(controller, "permissionMapper", permissionMapper);

        UserRole activeAssignment = assignment(1L, 10L);
        UserRole inactiveAssignment = assignment(1L, 11L);
        when(userRoleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(activeAssignment, inactiveAssignment));
        Role activeRole = role(10L, "ADMIN", CommonStatus.ACTIVE, false);
        Role inactiveRole = role(11L, "OLD_ADMIN", CommonStatus.INACTIVE, false);
        when(roleMapper.selectBatchIds(List.of(10L, 11L)))
                .thenReturn(List.of(activeRole, inactiveRole));
        when(rolePermissionService.getPermissionIdsByRoleId(10L))
                .thenReturn(List.of(100L, 101L));
        Permission activePermission = permission(100L, "api-permission:approve", "active", false);
        Permission inactivePermission = permission(
                101L, "api-permission:emergency-grant", "inactive", false);
        when(permissionMapper.selectBatchIds(List.of(100L, 101L)))
                .thenReturn(List.of(activePermission, inactivePermission));

        List<String> roles = ReflectionTestUtils.invokeMethod(
                controller, "getUserRoles", 1L);
        List<String> permissions = ReflectionTestUtils.invokeMethod(
                controller, "getUserPermissions", 1L);

        assertThat(roles).containsExactly("admin");
        assertThat(permissions).containsExactly("api-permission:approve");
    }

    private UserRole assignment(Long userId, Long roleId) {
        UserRole assignment = new UserRole();
        assignment.setUserId(userId);
        assignment.setRoleId(roleId);
        return assignment;
    }

    private Role role(Long id, String code, CommonStatus status, boolean deleted) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setStatus(status);
        role.setDeleted(deleted);
        return role;
    }

    private Permission permission(
            Long id, String code, String status, boolean deleted) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setPermissionCode(code);
        permission.setStatus(status);
        permission.setDeleted(deleted);
        return permission;
    }
}
