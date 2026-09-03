package com.dataplatform.identity.iam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.service.PermissionService;
import com.dataplatform.identity.iam.service.RolePermissionService;
import com.dataplatform.identity.iam.service.RoleService;
import com.dataplatform.identity.iam.service.UserRoleService;
import com.dataplatform.identity.iam.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IamAuthorizationServiceTest {

    private final UserService userService = mock(UserService.class);
    private final RoleService roleService = mock(RoleService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final UserRoleService userRoleService = mock(UserRoleService.class);
    private final RolePermissionService rolePermissionService = mock(RolePermissionService.class);
    private IamAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new IamAuthorizationService(
                userService,
                roleService,
                permissionService,
                userRoleService,
                rolePermissionService);
    }

    @Test
    void rejectsOrdinaryUserSelfRoleAssignmentBeforeMutation() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentUserId).thenReturn(42L);

            assertThatThrownBy(() -> authorizationService.prepareRoleAssignment(
                    42L, List.of(1L)))
                    .isInstanceOfSatisfying(
                            IamAuthorizationException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo("SELF_ROLE_MUTATION_FORBIDDEN"));
            verify(roleService, never()).listByIds(List.of(1L));
        }
    }

    @Test
    void rejectsCrossTenantUserManagement() {
        User target = user(20L, 8L);
        when(userService.getById(20L)).thenReturn(target);
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin"))
                    .thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            assertThatThrownBy(() -> authorizationService.requireUserInScope(20L))
                    .isInstanceOfSatisfying(
                            IamAuthorizationException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo("USER_TENANT_DENIED"));
        }
    }

    @Test
    void rejectsUserManagementWithoutTenantScope() {
        User target = user(20L, 7L);
        when(userService.getById(20L)).thenReturn(target);
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin"))
                    .thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(null);

            assertThatThrownBy(() -> authorizationService.requireUserInScope(20L))
                    .isInstanceOfSatisfying(
                            IamAuthorizationException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo("USER_TENANT_DENIED"));
        }
    }

    @Test
    void tenantFilterFailsClosedWithoutTenantScope() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin"))
                    .thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(null);

            assertThatThrownBy(authorizationService::tenantFilter)
                    .isInstanceOfSatisfying(
                            IamAuthorizationException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo("TENANT_SCOPE_REQUIRED"));
        }
    }

    @Test
    void platformAdminPermissionSatisfiesAnyPermissionCheck() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("user:delete"))
                    .thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("system:admin"))
                    .thenReturn(true);

            authorizationService.requirePermission("user:delete");
        }
    }

    @Test
    void rejectsRoleThatWouldEscalateActorPrivileges() {
        User target = user(20L, 7L);
        Role role = role(3L, CommonStatus.ACTIVE, false);
        Permission emergency = permission("api-permission:emergency-grant");
        when(userService.getById(20L)).thenReturn(target);
        when(roleService.listByIds(List.of(3L))).thenReturn(List.of(role));
        when(rolePermissionService.getPermissionsByRoleId(3L))
                .thenReturn(List.of(emergency));

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentUserId).thenReturn(10L);
            userContext.when(() -> UserContext.hasPermission("system:admin"))
                    .thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);
            userContext.when(UserContext::getCurrentPermissions)
                    .thenReturn(List.of("user:edit"));

            assertThatThrownBy(() -> authorizationService.prepareRoleAssignment(
                    20L, List.of(3L)))
                    .isInstanceOfSatisfying(
                            IamAuthorizationException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo("ROLE_PRIVILEGE_ESCALATION_FORBIDDEN"));
        }
    }

    @Test
    void failsClosedWhenRoleLookupReturnsNoResponse() {
        when(userService.getById(20L)).thenReturn(user(20L, 7L));
        when(roleService.listByIds(List.of(3L))).thenReturn(null);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentUserId).thenReturn(10L);
            userContext.when(() -> UserContext.hasPermission("system:admin"))
                    .thenReturn(true);

            assertThatThrownBy(() -> authorizationService.prepareRoleAssignment(20L, List.of(3L)))
                    .isInstanceOfSatisfying(
                            IamAuthorizationException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo("IAM_ROLE_LOOKUP_UNAVAILABLE"));
        }
    }

    @Test
    void failsClosedWhenRolePermissionsCannotBeLoaded() {
        when(userService.getById(20L)).thenReturn(user(20L, 7L));
        when(roleService.listByIds(List.of(3L))).thenReturn(List.of(role(3L, CommonStatus.ACTIVE, false)));
        when(rolePermissionService.getPermissionsByRoleId(3L)).thenReturn(null);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentUserId).thenReturn(10L);
            userContext.when(() -> UserContext.hasPermission("system:admin"))
                    .thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);
            userContext.when(UserContext::getCurrentPermissions).thenReturn(List.of("user:edit"));

            assertThatThrownBy(() -> authorizationService.prepareRoleAssignment(20L, List.of(3L)))
                    .isInstanceOfSatisfying(
                            IamAuthorizationException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo("IAM_PERMISSION_LOOKUP_UNAVAILABLE"));
        }
    }

    @Test
    void failsClosedWhenPermissionLookupReturnsNoResponse() {
        when(permissionService.listByIds(List.of(8L))).thenReturn(null);

        assertThatThrownBy(() -> authorizationService.preparePermissionAssignment(3L, List.of(8L)))
                .isInstanceOfSatisfying(
                        IamAuthorizationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("IAM_PERMISSION_LOOKUP_UNAVAILABLE"));
    }

    @Test
    void failsClosedWhenRoleUsageLookupReturnsNoResponse() {
        when(userRoleService.getUserIdsByRoleId(3L)).thenReturn(null);

        assertThatThrownBy(() -> authorizationService.requireRoleNotAssigned(3L))
                .isInstanceOfSatisfying(
                        IamAuthorizationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("IAM_USER_ROLE_LOOKUP_UNAVAILABLE"));
    }

    @Test
    void logsOutTargetBeforeAuthorizedRoleAssignment() {
        User target = user(20L, 7L);
        Role role = role(3L, CommonStatus.ACTIVE, false);
        when(userService.getById(20L)).thenReturn(target);
        when(roleService.listByIds(List.of(3L))).thenReturn(List.of(role));

        try (var userContext = mockStatic(UserContext.class);
             var stpUtil = mockStatic(StpUtil.class)) {
            userContext.when(UserContext::getCurrentUserId).thenReturn(10L);
            userContext.when(() -> UserContext.hasPermission("system:admin"))
                    .thenReturn(true);

            authorizationService.prepareRoleAssignment(20L, List.of(3L));

            stpUtil.verify(() -> StpUtil.logout(20L));
        }
    }

    @Test
    void invalidatesEverySessionAffectedByRolePermissionMutation() {
        when(userRoleService.getUserIdsByRoleId(3L)).thenReturn(List.of(20L, 21L));
        try (var stpUtil = mockStatic(StpUtil.class)) {
            authorizationService.invalidateUsersWithRole(3L);

            stpUtil.verify(() -> StpUtil.logout(20L));
            stpUtil.verify(() -> StpUtil.logout(21L));
        }
    }

    @Test
    void invalidatesEverySessionInTenantWhenTenantIsDisabled() {
        when(userService.listUserIdsByTenant(7L)).thenReturn(List.of(20L, 21L));

        try (var stpUtil = mockStatic(StpUtil.class)) {
            authorizationService.invalidateUsersInTenant(7L);

            stpUtil.verify(() -> StpUtil.logout(20L));
            stpUtil.verify(() -> StpUtil.logout(21L));
        }
    }

    private User user(Long id, Long tenantId) {
        User user = new User();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setDeleted(false);
        return user;
    }

    private Role role(Long id, CommonStatus status, boolean deleted) {
        Role role = new Role();
        role.setId(id);
        role.setStatus(status);
        role.setDeleted(deleted);
        return role;
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setPermissionCode(code);
        permission.setStatus("active");
        permission.setDeleted(false);
        return permission;
    }
}
