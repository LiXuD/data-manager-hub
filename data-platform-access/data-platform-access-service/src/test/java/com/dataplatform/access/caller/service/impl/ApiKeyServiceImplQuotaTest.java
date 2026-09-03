package com.dataplatform.access.caller.service.impl;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.mapper.ApiKeyMapper;
import com.dataplatform.access.caller.service.ApiKeyCacheService;
import com.dataplatform.common.enums.ApiKeyStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceImplQuotaTest {

    @Test
    void shouldRejectOverflowBeforeCallingTheDatabase() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        ApiKey key = key(Long.MAX_VALUE - 1, Long.MAX_VALUE);
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(key);
        ApiKeyServiceImpl service = serviceWith(mapper);

        assertFalse(service.validateAndConsumeQuota("dp_quota", 2));
        verify(mapper, never()).consumeQuota(any(), anyLong());
    }

    @Test
    void shouldUseAnAtomicDatabaseGuardForConcurrentQuotaConsumption() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        ApiKey key = key(9L, 10L);
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(key);
        when(mapper.consumeQuota(1L, 1L)).thenReturn(1);
        when(mapper.selectById(1L)).thenReturn(key);
        ApiKeyCacheService cacheService = mock(ApiKeyCacheService.class);
        ApiKeyServiceImpl service = serviceWith(mapper, cacheService);

        assertTrue(service.validateAndConsumeQuota("dp_quota", 1));
        verify(mapper).consumeQuota(1L, 1L);
        verify(cacheService).syncAfterCommit(key);
    }

    @Test
    void shouldRejectCorruptedNegativeUsageWithoutWriting() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        ApiKey key = key(-1L, 10L);
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(key);
        ApiKeyServiceImpl service = serviceWith(mapper);

        assertFalse(service.validateAndConsumeQuota("dp_quota", 1));
        verify(mapper, never()).consumeQuota(any(), anyLong());
    }

    private ApiKeyServiceImpl serviceWith(ApiKeyMapper mapper) {
        return serviceWith(mapper, mock(ApiKeyCacheService.class));
    }

    private ApiKeyServiceImpl serviceWith(ApiKeyMapper mapper, ApiKeyCacheService cacheService) {
        ApiKeyServiceImpl service = new ApiKeyServiceImpl(cacheService);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private ApiKey key(long used, long limit) {
        return key(Long.valueOf(used), Long.valueOf(limit));
    }

    private ApiKey key(Long used, Long limit) {
        ApiKey key = new ApiKey();
        key.setId(1L);
        key.setApiKey("dp_quota");
        key.setStatus(ApiKeyStatus.ACTIVE);
        key.setExpireTime(LocalDateTime.now().plusMinutes(5));
        key.setQuotaUsed(used);
        key.setQuotaLimit(limit);
        return key;
    }
}
