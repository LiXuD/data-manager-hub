package com.dataplatform.access.caller.service;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.vo.CurrentUserApiKeyOptionsVO;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.identity.api.feign.IdentityAccessInternalFeignClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CurrentUserApiKeyOptionServiceTest {

    private final IdentityAccessInternalFeignClient identityAccessClient =
            mock(IdentityAccessInternalFeignClient.class);
    private final CallerService callerService = mock(CallerService.class);
    private final ApiKeyService apiKeyService = mock(ApiKeyService.class);
    private final CurrentUserApiKeyOptionService service = new CurrentUserApiKeyOptionService(
            identityAccessClient, callerService, apiKeyService);

    @Test
    void returnsEmptyAssociationStateWhenUserHasNoCaller() {
        when(identityAccessClient.getCallerIds(10L)).thenReturn(Result.success(List.of()));

        CurrentUserApiKeyOptionsVO result = service.listOptions(10L, 20L);

        assertFalse(result.isHasAssociatedCaller());
        assertTrue(result.getOptions().isEmpty());
        verifyNoInteractions(callerService, apiKeyService);
    }

    @Test
    void returnsOnlyUsableKeysFromActiveCallersInCurrentTenant() {
        CallerInfo availableCaller = caller(1L, 20L, "核心系统", CommonStatus.ACTIVE);
        CallerInfo otherTenantCaller = caller(2L, 99L, "其他租户系统", CommonStatus.ACTIVE);
        CallerInfo inactiveCaller = caller(3L, 20L, "停用系统", CommonStatus.INACTIVE);
        when(identityAccessClient.getCallerIds(10L)).thenReturn(Result.success(List.of(1L, 2L, 3L)));
        when(callerService.listByIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(availableCaller, otherTenantCaller, inactiveCaller));

        ApiKey active = key(11L, 1L, "primary", "dp_live_1234567890", ApiKeyStatus.ACTIVE, null);
        ApiKey expired = key(12L, 1L, "expired", "dp_old_1234567890", ApiKeyStatus.ACTIVE,
                LocalDateTime.now().minusMinutes(1));
        ApiKey revoked = key(13L, 1L, "revoked", "dp_revoked_123456", ApiKeyStatus.REVOKED, null);
        when(apiKeyService.listByCaller(1L)).thenReturn(List.of(active, expired, revoked));

        CurrentUserApiKeyOptionsVO result = service.listOptions(10L, 20L);

        assertTrue(result.isHasAssociatedCaller());
        assertEquals(1, result.getOptions().size());
        assertEquals(11L, result.getOptions().get(0).getId());
        assertEquals("核心系统", result.getOptions().get(0).getCallerName());
        assertEquals("dp_l****7890", result.getOptions().get(0).getMaskedApiKey());
        when(apiKeyService.getById(11L)).thenReturn(active);
        assertEquals(active, service.findUsableKey(10L, 20L, 11L));
        assertEquals(null, service.findUsableKey(10L, 20L, 99L));
    }

    @Test
    void failsClosedWhenIdentityServiceReturnsInvalidResponse() {
        when(identityAccessClient.getCallerIds(10L)).thenReturn(Result.error(500, "error"));

        assertThrows(IllegalStateException.class, () -> service.listOptions(10L, 20L));
        verifyNoInteractions(callerService, apiKeyService);
    }

    private CallerInfo caller(Long id, Long tenantId, String name, CommonStatus status) {
        CallerInfo caller = new CallerInfo();
        caller.setId(id);
        caller.setTenantId(tenantId);
        caller.setCallerCode("caller-" + id);
        caller.setCallerName(name);
        caller.setStatus(status);
        return caller;
    }

    private ApiKey key(
            Long id,
            Long callerId,
            String name,
            String value,
            ApiKeyStatus status,
            LocalDateTime expireTime) {
        ApiKey key = new ApiKey();
        key.setId(id);
        key.setCallerId(callerId);
        key.setKeyName(name);
        key.setApiKey(value);
        key.setStatus(status);
        key.setExpireTime(expireTime);
        return key;
    }
}
