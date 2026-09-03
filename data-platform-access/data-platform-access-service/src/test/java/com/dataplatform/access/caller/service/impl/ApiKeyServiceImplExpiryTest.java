package com.dataplatform.access.caller.service.impl;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.mapper.ApiKeyMapper;
import com.dataplatform.access.caller.service.ApiKeyCacheService;
import com.dataplatform.access.caller.service.ApiKeyProvisioningException;
import com.dataplatform.common.enums.ApiKeyStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyServiceImplExpiryTest {

    @Test
    void shouldRejectBlankApiKeyBeforeQuerying() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        ApiKeyServiceImpl service = serviceWith(mapper);

        assertNull(service.getByKey("  "));
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectExpiredActiveApiKey() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        ApiKey expired = key(LocalDateTime.now().minusSeconds(1));
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(expired);
        ApiKeyServiceImpl service = serviceWith(mapper);

        assertNull(service.getByKey("dp_expired"));
    }

    @Test
    void shouldReturnActiveApiKeyBeforeExpiry() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        ApiKey active = key(LocalDateTime.now().plusSeconds(1));
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(active);
        ApiKeyServiceImpl service = serviceWith(mapper);

        assertSame(active, service.getByKey("dp_active"));
    }

    @Test
    void shouldRejectFailedPersistenceBeforePublishingTheKey() {
        ApiKeyCacheService cacheService = mock(ApiKeyCacheService.class);
        ApiKeyServiceImpl service = spy(new ApiKeyServiceImpl(cacheService));
        doReturn(false).when(service).save(any(ApiKey.class));

        assertThrows(ApiKeyProvisioningException.class,
                () -> service.createApiKey(3L, "production"));
        verifyNoInteractions(cacheService);
    }

    private ApiKeyServiceImpl serviceWith(ApiKeyMapper mapper) {
        ApiKeyServiceImpl service = new ApiKeyServiceImpl(mock(ApiKeyCacheService.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private ApiKey key(LocalDateTime expireTime) {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(1L);
        apiKey.setApiKey(expireTime.isBefore(LocalDateTime.now()) ? "dp_expired" : "dp_active");
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setExpireTime(expireTime);
        return apiKey;
    }
}
