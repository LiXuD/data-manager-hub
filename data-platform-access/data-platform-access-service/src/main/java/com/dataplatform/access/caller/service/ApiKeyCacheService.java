package com.dataplatform.access.caller.service;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApiKeyCacheService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyCacheService.class);
    private static final String OPENAPI_KEY_PREFIX = "openapi:key:";
    private static final String RATE_LIMIT_PREFIX = "openapi:rate_limit:";
    private static final int DEFAULT_WINDOW_SEC = 60;

    private final RedisTemplate<String, Object> redisTemplate;
    private final CallerService callerService;

    public ApiKeyCacheService(RedisTemplate<String, Object> redisTemplate, CallerService callerService) {
        this.redisTemplate = redisTemplate;
        this.callerService = callerService;
    }

    public void sync(ApiKey apiKey) {
        if (apiKey == null || !StringUtils.hasText(apiKey.getApiKey())) {
            return;
        }
        if (!isUsableKey(apiKey)) {
            evict(apiKey);
            return;
        }

        try {
            CallerInfo caller = callerService.getById(apiKey.getCallerId());
            if (caller == null) {
                evict(apiKey);
                return;
            }
            boolean callerActive = caller.getStatus() == CommonStatus.ACTIVE;
            redisTemplate.opsForValue().set(OPENAPI_KEY_PREFIX + apiKey.getApiKey(), Map.of(
                    "keyId", apiKey.getId(),
                    "callerId", apiKey.getCallerId(),
                    "callerName", caller.getCallerName() != null ? caller.getCallerName() : "",
                    "status", callerActive ? 1 : 0
            ));
            redisTemplate.opsForValue().set(RATE_LIMIT_PREFIX + apiKey.getId(), Map.of(
                    "enabled", !Boolean.FALSE.equals(apiKey.getRateLimitEnabled()),
                    "windowSec", DEFAULT_WINDOW_SEC,
                    "maxReqs", apiKey.getRateLimit() != null ? apiKey.getRateLimit() : 100
            ));
        } catch (Exception e) {
            log.warn("同步API Key网关缓存失败: keyId={}, error={}", apiKey.getId(), e.getMessage());
        }
    }

    public void evict(ApiKey apiKey) {
        if (apiKey == null) {
            return;
        }
        try {
            if (StringUtils.hasText(apiKey.getApiKey())) {
                redisTemplate.delete(OPENAPI_KEY_PREFIX + apiKey.getApiKey());
            }
            if (apiKey.getId() != null) {
                redisTemplate.delete(RATE_LIMIT_PREFIX + apiKey.getId());
            }
        } catch (Exception e) {
            log.warn("清理API Key网关缓存失败: keyId={}, error={}", apiKey.getId(), e.getMessage());
        }
    }

    private boolean isUsableKey(ApiKey apiKey) {
        if (apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            return false;
        }
        return apiKey.getExpireTime() == null || !apiKey.getExpireTime().isBefore(LocalDateTime.now());
    }
}
