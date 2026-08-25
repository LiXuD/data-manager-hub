package com.dataplatform.identity.iam.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dataplatform.identity.iam.security.IamAuthorizationException;
import com.dataplatform.identity.iam.security.IamAuthorizationService;
import com.dataplatform.identity.iam.service.PermissionService;
import com.dataplatform.identity.iam.service.RolePermissionService;
import com.dataplatform.identity.iam.service.RoleService;
import com.dataplatform.identity.iam.service.UserCallerService;
import com.dataplatform.identity.iam.service.UserRoleService;
import com.dataplatform.identity.iam.service.UserService;
import com.dataplatform.identity.security.service.PasswordService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class IamManagementControllerSecurityTest {

    @Test
    void ordinaryAuthenticatedUserCannotReachRoleAssignmentMutation() {
        UserRoleService userRoleService = mock(UserRoleService.class);
        IamAuthorizationService authorizationService = mock(IamAuthorizationService.class);
        UserController controller = new UserController(
                mock(UserService.class),
                mock(UserCallerService.class),
                userRoleService,
                mock(PasswordService.class),
                authorizationService);
        IamAuthorizationException denied = forbidden();
        org.mockito.Mockito.doThrow(denied)
                .when(authorizationService).requirePermission("user:edit");

        assertThatThrownBy(() -> controller.assignRoles(42L, List.of(1L)))
                .isSameAs(denied);
        verify(userRoleService, never()).assignRoles(42L, List.of(1L));
    }

    @Test
    void nonPlatformRoleEditorCannotMutateGlobalRolePermissions() {
        RolePermissionService rolePermissionService = mock(RolePermissionService.class);
        IamAuthorizationService authorizationService = mock(IamAuthorizationService.class);
        RoleController controller = new RoleController(
                mock(RoleService.class), rolePermissionService, authorizationService);
        IamAuthorizationException denied = forbidden();
        org.mockito.Mockito.doThrow(denied)
                .when(authorizationService).requirePlatformAdmin();

        assertThatThrownBy(() -> controller.assignPermissions(1L, List.of(2L)))
                .isSameAs(denied);
        verify(rolePermissionService, never()).assignPermissions(1L, List.of(2L));
    }

    @Test
    void permissionCatalogReadsRequireRoleView() {
        IamAuthorizationService authorizationService = mock(IamAuthorizationService.class);
        PermissionService permissionService = mock(PermissionService.class);
        PermissionController controller =
                new PermissionController(permissionService, authorizationService);
        IamAuthorizationException denied = forbidden();
        org.mockito.Mockito.doThrow(denied)
                .when(authorizationService).requirePermission("role:view");

        assertThatThrownBy(controller::listAllActive).isSameAs(denied);
        verify(permissionService, never()).listAllActive();
    }

    private IamAuthorizationException forbidden() {
        return new IamAuthorizationException(
                HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "权限不足");
    }
}
