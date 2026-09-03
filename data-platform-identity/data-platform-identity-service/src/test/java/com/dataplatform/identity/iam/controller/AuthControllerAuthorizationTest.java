package com.dataplatform.identity.iam.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.entity.UserRole;
import com.dataplatform.identity.iam.mapper.PermissionMapper;
import com.dataplatform.identity.iam.mapper.RoleMapper;
import com.dataplatform.identity.iam.mapper.UserMapper;
import com.dataplatform.identity.iam.mapper.UserRoleMapper;
import com.dataplatform.identity.iam.service.RolePermissionService;
import com.dataplatform.identity.tenant.entity.TenantInfo;
import com.dataplatform.identity.tenant.mapper.TenantMapper;
import com.dataplatform.identity.security.service.PasswordService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthControllerAuthorizationTest {

    @Test
    void rejectsNullLoginBody() {
        AuthController controller = new AuthController();

        Result<Map<String, Object>> result = controller.login(null);

        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    void rejectsNullProfileBodyBeforeWriting() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);

        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::isLoggedIn).thenReturn(true);

            Result<Map<String, Object>> result = controller.updateProfile(null);

            assertThat(result.getCode()).isEqualTo(400);
            verifyNoInteractions(userMapper);
        }
    }

    @Test
    void rejectsNullPasswordBodyBeforeReadingUser() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);

        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::isLoggedIn).thenReturn(true);

            Result<Void> result = controller.changePassword(null);

            assertThat(result.getCode()).isEqualTo(400);
            verifyNoInteractions(userMapper);
        }
    }

    @Test
    void rejectsPasswordChangeWhenPersistenceReportsNoRows() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        PasswordService passwordService = mock(PasswordService.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "passwordService", passwordService);

        User user = new User();
        user.setId(1L);
        user.setPassword("encoded-old");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordService.matches("oldPassword", "encoded-old")).thenReturn(true);
        when(passwordService.isStrongEnough("newPassword1")).thenReturn(true);
        when(passwordService.encode("newPassword1")).thenReturn("encoded-new");
        when(userMapper.updateById(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(0);

        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class);
             var stpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            userContext.when(UserContext::isLoggedIn).thenReturn(true);
            userContext.when(UserContext::getCurrentUserId).thenReturn(1L);

            Result<Void> result = controller.changePassword(Map.of(
                    "oldPassword", "oldPassword", "newPassword", "newPassword1"));

            assertThat(result.getCode()).isEqualTo(409);
            stpUtil.verify(() -> StpUtil.logout(1L), org.mockito.Mockito.never());
        }
    }

    @Test
    void invalidatedUserInfoSessionReturnsUnauthorized() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        User user = new User();
        user.setId(1L);
        user.setStatus(CommonStatus.INACTIVE);
        user.setDeleted(false);
        when(userMapper.selectById(1L)).thenReturn(user);

        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::isLoggedIn).thenReturn(true);
            userContext.when(UserContext::getCurrentUserId).thenReturn(1L);

            Result<Map<String, Object>> result = controller.getUserInfo();

            assertThat(result.getCode()).isEqualTo(401);
            userContext.verify(UserContext::logout);
        }
    }

    @Test
    void userInfoRefreshesPermissionsInTheCurrentSession() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RolePermissionService rolePermissionService = mock(RolePermissionService.class);
        PermissionMapper permissionMapper = mock(PermissionMapper.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(controller, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(controller, "rolePermissionService", rolePermissionService);
        ReflectionTestUtils.setField(controller, "permissionMapper", permissionMapper);

        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setStatus(CommonStatus.ACTIVE);
        user.setDeleted(false);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userRoleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(assignment(1L, 10L)));
        when(roleMapper.selectBatchIds(List.of(10L)))
                .thenReturn(List.of(role(10L, "admin", CommonStatus.ACTIVE, false)));
        when(rolePermissionService.getPermissionIdsByRoleId(10L)).thenReturn(List.of(100L));
        when(permissionMapper.selectBatchIds(List.of(100L)))
                .thenReturn(List.of(permission(100L, "connector-plugin:view", "active", false)));

        SaSession session = mock(SaSession.class);
        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class);
             var stpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            userContext.when(UserContext::isLoggedIn).thenReturn(true);
            userContext.when(UserContext::getCurrentUserId).thenReturn(1L);
            stpUtil.when(StpUtil::getSession).thenReturn(session);

            Result<Map<String, Object>> result = controller.getUserInfo();

            assertThat(result.getData()).containsEntry(
                    "permissions", List.of("connector-plugin:view"));
            verify(session).set(UserContext.PERMISSIONS_KEY, List.of("connector-plugin:view"));
        }
    }

    @Test
    void rejectsLoginForInactiveTenantBeforeCreatingSession() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        PasswordService passwordService = mock(PasswordService.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "passwordService", passwordService);
        ReflectionTestUtils.setField(controller, "tenantMapper", tenantMapper);

        User user = new User();
        user.setId(1L);
        user.setUsername("tenant-user");
        user.setPassword("encoded");
        user.setStatus(CommonStatus.ACTIVE);
        user.setTenantId(7L);
        TenantInfo tenant = new TenantInfo();
        tenant.setId(7L);
        tenant.setStatus("inactive");
        tenant.setDeleted(false);
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(passwordService.matches("password1", "encoded")).thenReturn(true);
        when(tenantMapper.selectById(7L)).thenReturn(tenant);

        Result<Map<String, Object>> result = controller.login(Map.of(
                "username", "tenant-user", "password", "password1"));

        assertThat(result.getCode()).isEqualTo(401);
        verify(userMapper, org.mockito.Mockito.never()).updateById(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void rejectsDeletedUserBeforeCreatingSession() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        PasswordService passwordService = mock(PasswordService.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "passwordService", passwordService);

        User user = new User();
        user.setId(1L);
        user.setUsername("deleted-user");
        user.setPassword("encoded");
        user.setStatus(CommonStatus.ACTIVE);
        user.setDeleted(true);
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(passwordService.matches("password1", "encoded")).thenReturn(true);

        Result<Map<String, Object>> result = controller.login(Map.of(
                "username", "deleted-user", "password", "password1"));

        assertThat(result.getCode()).isEqualTo(401);
        verify(userMapper, org.mockito.Mockito.never()).updateById(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void rejectsLoginWhenLegacyPasswordUpgradeCannotBePersisted() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        PasswordService passwordService = mock(PasswordService.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "passwordService", passwordService);

        User user = new User();
        user.setId(1L);
        user.setUsername("legacy-user");
        user.setPassword("legacy-password");
        user.setStatus(CommonStatus.ACTIVE);
        user.setDeleted(false);
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(passwordService.matches("password1", "legacy-password")).thenReturn(true);
        when(passwordService.isEncoded("legacy-password")).thenReturn(false);
        when(passwordService.encode("password1")).thenReturn("encoded-password");
        when(userMapper.updateById(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(0);

        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            Result<Map<String, Object>> result = controller.login(Map.of(
                    "username", "legacy-user", "password", "password1"));

            assertThat(result.getCode()).isEqualTo(409);
            userContext.verify(() -> UserContext.login(
                    org.mockito.ArgumentMatchers.eq(1L),
                    org.mockito.ArgumentMatchers.eq("legacy-user"),
                    org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.anyList()), org.mockito.Mockito.never());
        }
    }

    @Test
    void rejectsLoginWhenLastLoginCannotBePersisted() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        PasswordService passwordService = mock(PasswordService.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(controller, "passwordService", passwordService);

        User user = new User();
        user.setId(1L);
        user.setUsername("active-user");
        user.setPassword("encoded-password");
        user.setStatus(CommonStatus.ACTIVE);
        user.setDeleted(false);
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(passwordService.matches("password1", "encoded-password")).thenReturn(true);
        when(passwordService.isEncoded("encoded-password")).thenReturn(true);
        when(userRoleMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(userMapper.updateById(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(0);

        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            Result<Map<String, Object>> result = controller.login(Map.of(
                    "username", "active-user", "password", "password1"));

            assertThat(result.getCode()).isEqualTo(409);
            userContext.verify(() -> UserContext.login(
                    org.mockito.ArgumentMatchers.eq(1L),
                    org.mockito.ArgumentMatchers.eq("active-user"),
                    org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.anyList()), org.mockito.Mockito.never());
        }
    }

    @Test
    void invalidatesSessionWhenTenantIsInactive() {
        AuthController controller = new AuthController();
        UserMapper userMapper = mock(UserMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "tenantMapper", tenantMapper);

        User user = new User();
        user.setId(1L);
        user.setStatus(CommonStatus.ACTIVE);
        user.setDeleted(false);
        user.setTenantId(7L);
        TenantInfo tenant = new TenantInfo();
        tenant.setId(7L);
        tenant.setStatus("suspended");
        tenant.setDeleted(false);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(tenantMapper.selectById(7L)).thenReturn(tenant);

        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::isLoggedIn).thenReturn(true);
            userContext.when(UserContext::getCurrentUserId).thenReturn(1L);

            Result<Map<String, Object>> result = controller.getUserInfo();

            assertThat(result.getCode()).isEqualTo(401);
            userContext.verify(UserContext::logout);
        }
    }

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
