package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.util.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallerControllerTest {

    private final CallerService callerService = mock(CallerService.class);
    private final CallerController controller = new CallerController();

    CallerControllerTest() {
        ReflectionTestUtils.setField(controller, "callerService", callerService);
    }

    @Test
    void createScopesCallerToCurrentTenant() {
        CallerInfo caller = new CallerInfo();
        caller.setCallerCode("SYSTEM_A");
        caller.setCallerName("系统 A");
        caller.setTenantId(99L);
        when(callerService.getByCode("SYSTEM_A")).thenReturn(null);
        when(callerService.save(any(CallerInfo.class))).thenReturn(true);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            var response = controller.create(caller);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().getData().getTenantId()).isEqualTo(7L);
            verify(callerService).save(caller);
        }
    }

    @Test
    void refusesCallerWithoutTenantScope() {
        CallerInfo caller = new CallerInfo();
        caller.setCallerCode("SYSTEM_A");
        caller.setCallerName("系统 A");

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(null);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);

            var response = controller.create(caller);

            assertThat(response.getStatusCode().value()).isEqualTo(403);
            org.mockito.Mockito.verifyNoInteractions(callerService);
        }
    }

    @Test
    void cannotChooseATenantWhenCurrentUserHasNoTenantScope() {
        CallerInfo caller = new CallerInfo();
        caller.setCallerCode("SYSTEM_A");
        caller.setCallerName("系统 A");
        caller.setTenantId(99L);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(null);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);

            var response = controller.create(caller);

            assertThat(response.getStatusCode().value()).isEqualTo(403);
            org.mockito.Mockito.verifyNoInteractions(callerService);
        }
    }

    @Test
    void platformAdminMayProvideAnExplicitTenantWhenOutsideTenantScope() {
        CallerInfo caller = new CallerInfo();
        caller.setCallerCode("SYSTEM_B");
        caller.setCallerName("系统 B");
        caller.setTenantId(99L);
        when(callerService.getByCode("SYSTEM_B")).thenReturn(null);
        when(callerService.save(any(CallerInfo.class))).thenReturn(true);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(null);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);

            var response = controller.create(caller);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().getData().getTenantId()).isEqualTo(99L);
            verify(callerService).save(caller);
        }
    }

    @Test
    void updatePreservesOwnershipAndServerManagedFields() {
        CallerInfo existing = new CallerInfo();
        existing.setId(7L);
        existing.setCallerCode("SYSTEM_A");
        existing.setTenantId(7L);
        existing.setStatus(com.dataplatform.common.enums.CommonStatus.ACTIVE);

        CallerInfo request = new CallerInfo();
        request.setCallerCode("ATTACKER_CODE");
        request.setCallerName("  新名称  ");
        request.setTenantId(99L);
        request.setStatus(com.dataplatform.common.enums.CommonStatus.INACTIVE);
        request.setDeleted(true);
        when(callerService.getById(7L)).thenReturn(existing, existing);
        when(callerService.updateById(request)).thenReturn(true);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            var response = controller.update(7L, request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(request.getCallerCode()).isEqualTo("SYSTEM_A");
            assertThat(request.getCallerName()).isEqualTo("新名称");
            assertThat(request.getTenantId()).isEqualTo(7L);
            assertThat(request.getStatus()).isNull();
            assertThat(request.getDeleted()).isNull();
            verify(callerService).updateById(request);
        }
    }

    @Test
    void rejectsBlankCallerNameOnUpdate() {
        CallerInfo existing = new CallerInfo();
        existing.setId(7L);
        existing.setTenantId(7L);
        CallerInfo request = new CallerInfo();
        request.setCallerName("  ");
        when(callerService.getById(7L)).thenReturn(existing);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            assertThat(controller.update(7L, request).getStatusCode().value()).isEqualTo(400);
        }
    }
}
