package com.dataplatform.identity.tenant.controller;

import com.dataplatform.common.util.UserContext;
import com.dataplatform.identity.iam.security.IamAuthorizationService;
import com.dataplatform.identity.tenant.entity.TenantInfo;
import com.dataplatform.identity.tenant.service.TenantService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantControllerTest {

    private TenantService tenantService;
    private IamAuthorizationService authorizationService;
    private TenantController controller;

    @BeforeEach
    void setUp() {
        tenantService = mock(TenantService.class);
        authorizationService = mock(IamAuthorizationService.class);
        controller = new TenantController();
        ReflectionTestUtils.setField(controller, "tenantService", tenantService);
        ReflectionTestUtils.setField(controller, "authorizationService", authorizationService);
    }

    @Test
    void deniesListWithoutTenantScope() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(null);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);

            assertEquals(403, controller.list(1, 10, null, null).getCode());
        }
        verifyNoInteractions(tenantService);
    }

    @Test
    void platformAdminCreateUsesServerOwnedState() {
        TenantInfo request = new TenantInfo();
        request.setTenantCode("  tenant-a  ");
        request.setTenantName("  Tenant A  ");
        request.setStatus("suspended");
        request.setDeleted(true);
        when(tenantService.getByTenantCode("tenant-a")).thenReturn(null);
        when(tenantService.save(request)).thenReturn(true);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);

            assertEquals(200, controller.create(request).getStatusCode().value());
        }

        assertEquals("tenant-a", request.getTenantCode());
        assertEquals("Tenant A", request.getTenantName());
        assertEquals("active", request.getStatus());
        assertEquals(false, request.getDeleted());
        verify(tenantService).save(request);
    }

    @Test
    void updateCannotChangeCodeStatusOrServerManagedFields() {
        TenantInfo existing = tenant(7L, "tenant-a", "active");
        TenantInfo request = new TenantInfo();
        request.setTenantCode("attacker-code");
        request.setTenantName("Tenant A updated");
        request.setStatus("suspended");
        request.setDeleted(true);
        request.setCreatedBy(99L);
        when(tenantService.getById(7L)).thenReturn(existing, existing);
        when(tenantService.updateById(request)).thenReturn(true);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);

            assertEquals(200, controller.update(7L, request).getStatusCode().value());
        }

        assertEquals("tenant-a", request.getTenantCode());
        assertEquals("active", request.getStatus());
        assertNull(request.getDeleted());
        assertNull(request.getCreatedBy());
        verify(tenantService).updateById(request);
    }

    @Test
    void suspendedTenantCanOnlyBeRestoredToActive() {
        TenantInfo existing = tenant(7L, "tenant-a", "suspended");
        when(tenantService.getById(7L)).thenReturn(existing);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);

            assertEquals(409, controller.updateStatus(7L, Map.of("status", "inactive"))
                    .getStatusCode().value());
        }
        verify(tenantService).getById(7L);
        org.mockito.Mockito.verify(tenantService, org.mockito.Mockito.never()).updateById(any());
    }

    @Test
    void normalizesAndPersistsStatusOnce() {
        TenantInfo existing = tenant(7L, "tenant-a", "active");
        when(tenantService.getById(7L)).thenReturn(existing);
        when(tenantService.updateById(any(TenantInfo.class))).thenReturn(true);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);

            assertEquals(200, controller.updateStatus(7L, Map.of("status", " inactive "))
                    .getStatusCode().value());
        }

        ArgumentCaptor<TenantInfo> captor = ArgumentCaptor.forClass(TenantInfo.class);
        verify(tenantService).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals("inactive", captor.getValue().getStatus());
        verify(authorizationService).invalidateUsersInTenant(7L);
    }

    @Test
    void rejectsUnknownStatus() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);

            assertEquals(400, controller.updateStatus(7L, Map.of("status", "disabled"))
                    .getStatusCode().value());
        }
        verifyNoInteractions(tenantService);
    }

    private TenantInfo tenant(Long id, String code, String status) {
        TenantInfo tenant = new TenantInfo();
        tenant.setId(id);
        tenant.setTenantCode(code);
        tenant.setStatus(status);
        return tenant;
    }
}
