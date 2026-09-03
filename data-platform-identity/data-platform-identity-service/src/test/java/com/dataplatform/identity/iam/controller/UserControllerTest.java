package com.dataplatform.identity.iam.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.access.caller.api.feign.CallerInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.security.IamAuthorizationService;
import com.dataplatform.identity.iam.service.UserCallerService;
import com.dataplatform.identity.iam.service.UserRoleService;
import com.dataplatform.identity.iam.service.UserService;
import com.dataplatform.identity.security.service.PasswordService;
import com.dataplatform.identity.tenant.entity.TenantInfo;
import com.dataplatform.identity.tenant.service.TenantService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserControllerTest {

    private UserService userService;
    private PasswordService passwordService;
    private IamAuthorizationService authorizationService;
    private TenantService tenantService;
    private CallerInternalFeignClient callerInternalFeignClient;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        passwordService = mock(PasswordService.class);
        authorizationService = mock(IamAuthorizationService.class);
        tenantService = mock(TenantService.class);
        callerInternalFeignClient = mock(CallerInternalFeignClient.class);
        controller = new UserController(
                userService,
                mock(UserCallerService.class),
                mock(UserRoleService.class),
                passwordService,
                authorizationService,
                tenantService,
                callerInternalFeignClient);
    }

    @Test
    void createUsesTenantFromSessionAndServerOwnedState() {
        User request = new User();
        request.setUsername("  alice  ");
        request.setPassword("Password1");
        request.setTenantId(999L);
        request.setStatus(CommonStatus.INACTIVE);
        request.setDeleted(true);
        when(passwordService.isStrongEnough("Password1")).thenReturn(true);
        when(passwordService.encode("Password1")).thenReturn("encoded");
        when(userService.getByUsername("alice")).thenReturn(null);
        when(userService.save(request)).thenReturn(true);
        when(authorizationService.isPlatformAdmin()).thenReturn(false);

        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            assertEquals(200, controller.create(request).getStatusCode().value());
        }

        assertEquals("alice", request.getUsername());
        assertEquals(7L, request.getTenantId());
        assertEquals(CommonStatus.ACTIVE, request.getStatus());
        assertEquals(false, request.getDeleted());
        assertEquals("encoded", request.getPassword());
        assertNull(request.getCreatedBy());
        verify(userService).save(request);
    }

    @Test
    void createWithoutTenantScopeIsRejected() {
        User request = new User();
        request.setUsername("alice");
        request.setPassword("Password1");
        when(passwordService.isStrongEnough("Password1")).thenReturn(true);
        when(userService.getByUsername("alice")).thenReturn(null);
        when(authorizationService.isPlatformAdmin()).thenReturn(false);

        try (var userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(null);

            assertEquals(403, controller.create(request).getStatusCode().value());
        }
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void platformAdminCannotAssignUserToMissingOrInactiveTenant() {
        User request = new User();
        request.setUsername("alice");
        request.setPassword("Password1");
        request.setTenantId(99L);
        when(passwordService.isStrongEnough("Password1")).thenReturn(true);
        when(userService.getByUsername("alice")).thenReturn(null);
        when(authorizationService.isPlatformAdmin()).thenReturn(true);
        TenantInfo tenant = new TenantInfo();
        tenant.setId(99L);
        tenant.setStatus("suspended");
        tenant.setDeleted(false);
        when(tenantService.getById(99L)).thenReturn(tenant);

        assertEquals(400, controller.create(request).getStatusCode().value());
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void updatePreservesIdentityOwnershipAndServerFields() {
        User existing = user(10L, "alice", 7L, CommonStatus.ACTIVE);
        User request = new User();
        request.setUsername("alice");
        request.setNickname("Alice updated");
        request.setTenantId(999L);
        request.setStatus(CommonStatus.INACTIVE);
        request.setDeleted(true);
        request.setCreatedBy(99L);
        request.setCreatedAt(LocalDateTime.now());
        when(authorizationService.requireUserInScope(10L)).thenReturn(existing);
        when(userService.updateById(request)).thenReturn(true);
        when(userService.getById(10L)).thenReturn(existing);

        assertEquals(200, controller.update(10L, request).getStatusCode().value());

        assertEquals(10L, request.getId());
        assertEquals("alice", request.getUsername());
        assertEquals(7L, request.getTenantId());
        assertEquals(CommonStatus.ACTIVE, request.getStatus());
        assertNull(request.getDeleted());
        assertNull(request.getCreatedBy());
        assertNull(request.getCreatedAt());
        assertNull(request.getPassword());
        verify(authorizationService).invalidateUser(10L);
    }

    @Test
    void lockedStatusIsRejectedBecauseItIsNotAStoredStatus() {
        assertEquals(400, controller.updateStatus(10L, Map.of("status", "locked"))
                .getStatusCode().value());
        verify(authorizationService, never()).requireUserInScope(10L);
    }

    @Test
    void failedUpdateDoesNotInvalidateSession() {
        User existing = user(10L, "alice", 7L, CommonStatus.ACTIVE);
        User request = new User();
        request.setUsername("alice");
        request.setNickname("Alice");
        when(authorizationService.requireUserInScope(10L)).thenReturn(existing);
        when(userService.updateById(request)).thenReturn(false);

        assertEquals(409, controller.update(10L, request).getStatusCode().value());
        verify(authorizationService, never()).invalidateUser(10L);
    }

    @Test
    void nullAssignmentBodyIsRejectedBeforeMutation() {
        assertEquals(400, controller.assignRoles(10L, null).getStatusCode().value());
        verify(authorizationService, never()).prepareRoleAssignment(any(), any());
        verifyNoInteractions(userService);
    }

    @Test
    void callerAssignmentRejectsCallerFromAnotherTenantBeforeMutation() {
        User target = user(10L, "alice", 7L, CommonStatus.ACTIVE);
        when(authorizationService.requireUserInScope(10L)).thenReturn(target);
        when(callerInternalFeignClient.validate(7L, List.of(99L)))
                .thenReturn(Result.success(List.of()));

        assertEquals(400, controller.assignCallers(10L, List.of(99L)).getStatusCode().value());
        verify(userService, never()).save(any(User.class));
        verify(authorizationService, never()).invalidateUser(10L);
    }

    @Test
    void callerAssignmentValidatesBeforeInvalidatingSession() {
        User target = user(10L, "alice", 7L, CommonStatus.ACTIVE);
        UserCallerService userCallerService = mock(UserCallerService.class);
        controller = new UserController(
                userService,
                userCallerService,
                mock(UserRoleService.class),
                passwordService,
                authorizationService,
                tenantService,
                callerInternalFeignClient);
        when(authorizationService.requireUserInScope(10L)).thenReturn(target);
        when(callerInternalFeignClient.validate(7L, List.of(11L)))
                .thenReturn(Result.success(List.of(11L)));

        assertEquals(200, controller.assignCallers(10L, List.of(11L, 11L))
                .getStatusCode().value());
        verify(userCallerService).assignCallers(10L, List.of(11L));
        verify(authorizationService).invalidateUser(10L);
    }

    private User user(Long id, String username, Long tenantId, CommonStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setTenantId(tenantId);
        user.setStatus(status);
        user.setDeleted(false);
        return user;
    }
}
