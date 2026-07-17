package com.dataplatform.access.caller.service;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyCacheServiceTest {

    @Test
    void shouldSyncRateLimitPolicyForGateway() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        CallerService callerService = mock(CallerService.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CallerInfo caller = new CallerInfo();
        caller.setId(3L);
        caller.setCallerName("内部系统");
        caller.setStatus(CommonStatus.ACTIVE);
        when(callerService.getById(3L)).thenReturn(caller);

        ApiKey apiKey = new ApiKey();
        apiKey.setId(7L);
        apiKey.setCallerId(3L);
        apiKey.setApiKey("dp_test");
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setRateLimitEnabled(false);
        apiKey.setRateLimit(600);

        new ApiKeyCacheService(redisTemplate, callerService).sync(apiKey);

        verify(valueOperations).set(eq("openapi:rate_limit:7"), argThat(value -> {
            Map<?, ?> config = (Map<?, ?>) value;
            return Boolean.FALSE.equals(config.get("enabled"))
                    && Integer.valueOf(600).equals(config.get("maxReqs"))
                    && Integer.valueOf(60).equals(config.get("windowSec"));
        }));
    }
}
