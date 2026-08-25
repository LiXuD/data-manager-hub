package com.dataplatform.access.caller.service.impl;

import com.dataplatform.common.enums.ApiKeyStatus;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.mapper.ApiKeyMapper;
import com.dataplatform.access.caller.service.ApiKeyCacheService;
import com.dataplatform.access.caller.service.ApiKeyService;
import java.io.Serializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 访问域调用方的 Api Key Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
@Service
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> 
    implements ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyServiceImpl.class);
    private static final int MAX_RATE_LIMIT_PER_MINUTE = 1_000_000;

    private final ApiKeyCacheService apiKeyCacheService;

    public ApiKeyServiceImpl(ApiKeyCacheService apiKeyCacheService) {
        this.apiKeyCacheService = apiKeyCacheService;
    }

    @Override
    @Transactional
    public ApiKey createApiKey(Long callerId, String keyName) {
        ApiKey apiKey = new ApiKey();
        apiKey.setCallerId(callerId);
        apiKey.setKeyName(keyName);
        apiKey.setApiKey("dp_" + IdUtil.fastSimpleUUID());
        apiKey.setApiSecret(IdUtil.fastSimpleUUID());
        apiKey.setRateLimitEnabled(true);
        apiKey.setRateLimit(100);
        apiKey.setQuotaLimit(100000L);
        apiKey.setQuotaUsed(0L);
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setExpireTime(LocalDateTime.now().plusYears(1));
        save(apiKey);
        apiKeyCacheService.sync(apiKey);
        return apiKey;
    }

    @Override
    public boolean updateById(ApiKey entity) {
        boolean updated = super.updateById(entity);
        if (updated && entity != null) {
            ApiKey latest = entity.getId() != null ? getById(entity.getId()) : entity;
            apiKeyCacheService.sync(latest != null ? latest : entity);
        }
        return updated;
    }

    @Override
    public boolean removeById(Serializable id) {
        ApiKey existing = id != null ? getById(id) : null;
        boolean removed = super.removeById(id);
        if (removed) {
            apiKeyCacheService.evict(existing);
        }
        return removed;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncActiveKeysOnStartup() {
        List<ApiKey> activeKeys = list(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getStatus, ApiKeyStatus.ACTIVE.getCode()));
        activeKeys.forEach(apiKeyCacheService::sync);
        log.info("已同步可用 API Key 到网关缓存: count={}", activeKeys.size());
    }

    @Override
    public List<ApiKey> listByCaller(Long callerId) {
        return list(new LambdaQueryWrapper<ApiKey>()
            .eq(ApiKey::getCallerId, callerId)
            .eq(ApiKey::getStatus, "active")
            .orderByDesc(ApiKey::getId));
    }

    @Override
    public ApiKey getByKey(String apiKey) {
        return getOne(new LambdaQueryWrapper<ApiKey>()
            .eq(ApiKey::getApiKey, apiKey)
            .eq(ApiKey::getStatus, "active"));
    }

    @Override
    @Transactional
    public ApiKey updateRateLimitPolicy(Long id, boolean rateLimitEnabled, int rateLimit) {
        if (id == null || rateLimit < 1 || rateLimit > MAX_RATE_LIMIT_PER_MINUTE) {
            throw new IllegalArgumentException("每分钟最大请求数必须在1到1000000之间");
        }
        ApiKey apiKey = getById(id);
        if (apiKey == null) {
            return null;
        }
        apiKey.setRateLimitEnabled(rateLimitEnabled);
        apiKey.setRateLimit(rateLimit);
        return updateById(apiKey) ? apiKey : null;
    }

    @Override
    @Transactional
    public boolean validateAndConsumeQuota(String apiKey, long count) {
        ApiKey key = getByKey(apiKey);
        if (key == null) return false;
        if (key.getExpireTime() != null && key.getExpireTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        long used = key.getQuotaUsed() != null ? key.getQuotaUsed() : 0L;
        long limit = key.getQuotaLimit() != null ? key.getQuotaLimit() : 0L;
        if (count <= 0 || limit <= 0 || used + count > limit) {
            return false;
        }
        key.setQuotaUsed(used + count);
        return updateById(key);
    }
}
