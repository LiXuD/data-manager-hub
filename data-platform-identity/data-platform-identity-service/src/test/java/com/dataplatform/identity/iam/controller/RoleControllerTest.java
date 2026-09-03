package com.dataplatform.identity.iam.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.security.IamAuthorizationService;
import com.dataplatform.identity.iam.service.RolePermissionService;
import com.dataplatform.identity.iam.service.RoleService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleControllerTest {

    private RoleService roleService;
    private IamAuthorizationService authorizationService;
    private RoleController controller;

    @BeforeEach
    void setUp() {
        roleService = mock(RoleService.class);
        authorizationService = mock(IamAuthorizationService.class);
        controller = new RoleController(
                roleService, mock(RolePermissionService.class), authorizationService);
    }

    @Test
    void createNormalizesCodeAndOwnsServerFields() {
        Role request = new Role();
        request.setRoleCode("  Sales_Admin ");
        request.setRoleName("  Sales administrator  ");
        request.setStatus(CommonStatus.INACTIVE);
        request.setDeleted(true);
        when(authorizationService.canonicalRoleCode("  Sales_Admin ")).thenReturn("sales_admin");
        when(roleService.getByRoleCode("sales_admin")).thenReturn(null);
        when(roleService.save(request)).thenReturn(true);

        assertEquals(200, controller.create(request).getStatusCode().value());

        assertEquals("sales_admin", request.getRoleCode());
        assertEquals("Sales administrator", request.getRoleName());
        assertEquals(CommonStatus.ACTIVE, request.getStatus());
        assertEquals(false, request.getDeleted());
        assertNull(request.getCreatedBy());
        verify(roleService).save(request);
    }

    @Test
    void updateKeepsRoleCodeAndRejectsClientOwnedState() {
        Role existing = role(4L, "sales_admin", "Sales", CommonStatus.ACTIVE);
        Role request = new Role();
        request.setRoleCode("sales_admin");
        request.setRoleName(" Sales updated ");
        request.setStatus(CommonStatus.INACTIVE);
        request.setDeleted(true);
        request.setCreatedBy(42L);
        request.setCreatedAt(LocalDateTime.now());
        when(roleService.getById(4L)).thenReturn(existing, existing);
        when(authorizationService.canonicalRoleCode("sales_admin")).thenReturn("sales_admin");
        when(roleService.updateById(request)).thenReturn(true);

        assertEquals(200, controller.update(4L, request).getStatusCode().value());

        assertEquals("sales_admin", request.getRoleCode());
        assertEquals("Sales updated", request.getRoleName());
        assertEquals(CommonStatus.ACTIVE, request.getStatus());
        assertNull(request.getDeleted());
        assertNull(request.getCreatedBy());
        verify(authorizationService).invalidateUsersWithRole(4L);
    }

    @Test
    void updateStatusNormalizesAndIsIdempotent() {
        Role existing = role(4L, "sales_admin", "Sales", CommonStatus.ACTIVE);
        when(roleService.getById(4L)).thenReturn(existing);

        assertEquals(200, controller.updateStatus(4L, Map.of("status", " ACTIVE "))
                .getStatusCode().value());
        verify(roleService, never()).updateById(any(Role.class));
    }

    @Test
    void failedDeleteReturnsConflict() {
        Role existing = role(4L, "sales_admin", "Sales", CommonStatus.ACTIVE);
        when(roleService.getById(4L)).thenReturn(existing);
        when(roleService.removeById(4L)).thenReturn(false);

        assertEquals(409, controller.delete(4L).getStatusCode().value());
        verify(authorizationService, never()).invalidateUsersWithRole(4L);
    }

    private Role role(Long id, String code, String name, CommonStatus status) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setStatus(status);
        role.setDeleted(false);
        return role;
    }
}
