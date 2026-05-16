package com.dataplatform.access.caller.service.impl;

import com.dataplatform.common.enums.ApiKeyStatus;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.mapper.ApiKeyMapper;
import com.dataplatform.access.caller.service.ApiKeyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> 
    implements ApiKeyService {

    @Override
    @Transactional
    public ApiKey createApiKey(Long callerId) {
        ApiKey apiKey = new ApiKey();
        apiKey.setCallerId(callerId);
        apiKey.setApiKey("dp_" + IdUtil.fastSimpleUUID());
        apiKey.setApiSecret(IdUtil.fastSimpleUUID());
        apiKey.setRateLimit(100);
        apiKey.setQuotaLimit(100000L);
        apiKey.setQuotaUsed(0L);
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setExpireTime(LocalDateTime.now().plusYears(1));
        save(apiKey);
        return apiKey;
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
    public boolean validateAndConsumeQuota(String apiKey, long count) {
        ApiKey key = getByKey(apiKey);
        if (key == null) return false;
        if (key.getExpireTime() != null && key.getExpireTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        if (key.getQuotaUsed() + count > key.getQuotaLimit()) {
            return false;
        }
        key.setQuotaUsed(key.getQuotaUsed() + count);
        return updateById(key);
    }
}
