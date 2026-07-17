package com.dataplatform.access.caller.service.impl;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.mapper.ApiKeyMapper;
import com.dataplatform.access.caller.service.ApiKeyCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceImplRateLimitTest {

    @Test
    void shouldPersistPolicyAndRefreshGatewayCache() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        ApiKeyCacheService cacheService = mock(ApiKeyCacheService.class);
        ApiKeyServiceImpl service = new ApiKeyServiceImpl(cacheService);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        ApiKey apiKey = new ApiKey();
        apiKey.setId(7L);
        apiKey.setRateLimitEnabled(true);
        apiKey.setRateLimit(100);
        when(mapper.selectById(7L)).thenReturn(apiKey);
        when(mapper.updateById(any(ApiKey.class))).thenReturn(1);

        ApiKey updated = service.updateRateLimitPolicy(7L, false, 600);

        assertFalse(updated.getRateLimitEnabled());
        assertEquals(600, updated.getRateLimit());
        verify(mapper).updateById(apiKey);
        verify(cacheService).sync(apiKey);
    }
}
