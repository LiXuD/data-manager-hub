package com.dataplatform.access.caller.service;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
            Map<String, Object> keyInfo = new HashMap<>();
            keyInfo.put("keyId", apiKey.getId());
            keyInfo.put("callerId", apiKey.getCallerId());
            keyInfo.put("callerName", caller.getCallerName() != null ? caller.getCallerName() : "");
            keyInfo.put("status", callerActive ? 1 : 0);
            if (apiKey.getExpireTime() != null) {
                keyInfo.put("expireAtEpochMs", apiKey.getExpireTime()
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            redisTemplate.opsForValue().set(OPENAPI_KEY_PREFIX + apiKey.getApiKey(), keyInfo);
            redisTemplate.opsForValue().set(RATE_LIMIT_PREFIX + apiKey.getId(), Map.of(
                    "enabled", !Boolean.FALSE.equals(apiKey.getRateLimitEnabled()),
                    "windowSec", DEFAULT_WINDOW_SEC,
                    "maxReqs", apiKey.getRateLimit() != null ? apiKey.getRateLimit() : 100
            ));
        } catch (Exception e) {
            log.warn("同步API Key网关缓存失败: keyId={}, error={}", apiKey.getId(), e.getMessage());
        }
    }

    /**
     * Publish a cache change only after the database transaction has committed.
     * A direct, non-transactional caller still gets an immediate best-effort sync.
     */
    public void syncAfterCommit(ApiKey apiKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sync(apiKey);
                }
            });
        } else {
            sync(apiKey);
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

    public void evictAfterCommit(ApiKey apiKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(apiKey);
                }
            });
        } else {
            evict(apiKey);
        }
    }

    private boolean isUsableKey(ApiKey apiKey) {
        if (apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            return false;
        }
        return apiKey.getExpireTime() == null || apiKey.getExpireTime().isAfter(LocalDateTime.now());
    }
}
