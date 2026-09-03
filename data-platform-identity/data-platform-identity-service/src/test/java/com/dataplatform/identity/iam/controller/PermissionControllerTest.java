package com.dataplatform.identity.iam.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.security.IamAuthorizationService;
import com.dataplatform.identity.iam.service.PermissionService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionControllerTest {

    private PermissionService permissionService;
    private IamAuthorizationService authorizationService;
    private PermissionController controller;

    @BeforeEach
    void setUp() {
        permissionService = mock(PermissionService.class);
        authorizationService = mock(IamAuthorizationService.class);
        controller = new PermissionController(permissionService, authorizationService);
    }

    @Test
    void createDefaultsToButtonAndOwnsServerFields() {
        Permission request = new Permission();
        request.setPermissionCode("  report:view ");
        request.setPermissionName("  View reports  ");
        request.setStatus("inactive");
        request.setDeleted(true);
        when(authorizationService.canonicalPermissionCode("  report:view "))
                .thenReturn("report:view");
        when(permissionService.getOne(any())).thenReturn(null);
        when(permissionService.save(request)).thenReturn(true);

        assertEquals(200, controller.create(request).getStatusCode().value());

        assertEquals("report:view", request.getPermissionCode());
        assertEquals("View reports", request.getPermissionName());
        assertEquals("button", request.getResourceType());
        assertEquals("active", request.getStatus());
        assertEquals(false, request.getDeleted());
        assertNull(request.getCreatedBy());
        verify(permissionService).save(request);
    }

    @Test
    void updatePreservesPermissionCodeAndStatus() {
        Permission existing = permission(8L, "report:view", "View reports", "active");
        Permission request = new Permission();
        request.setPermissionCode("report:view");
        request.setPermissionName(" View reports updated ");
        request.setResourceType(" PAGE ");
        request.setStatus("inactive");
        request.setDeleted(true);
        request.setCreatedBy(99L);
        request.setCreatedAt(LocalDateTime.now());
        when(permissionService.getById(8L)).thenReturn(existing, existing);
        when(authorizationService.canonicalPermissionCode("report:view"))
                .thenReturn("report:view");
        when(permissionService.updateById(request)).thenReturn(true);

        assertEquals(200, controller.update(8L, request).getStatusCode().value());

        assertEquals("report:view", request.getPermissionCode());
        assertEquals("View reports updated", request.getPermissionName());
        assertEquals("page", request.getResourceType());
        assertEquals("active", request.getStatus());
        assertNull(request.getDeleted());
        assertNull(request.getCreatedBy());
        verify(authorizationService).invalidateUsersWithPermission(8L);
    }

    @Test
    void failedDeleteReturnsConflictWithoutInvalidatingAgain() {
        Permission existing = permission(8L, "report:view", "View reports", "active");
        when(permissionService.getById(8L)).thenReturn(existing);
        when(permissionService.removeById(8L)).thenReturn(false);

        assertEquals(409, controller.delete(8L).getStatusCode().value());
        verify(authorizationService, never()).invalidateUsersWithPermission(8L);
    }

    private Permission permission(Long id, String code, String name, String status) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setStatus(status);
        permission.setDeleted(false);
        return permission;
    }
}
