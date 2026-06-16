package com.dataplatform.access.caller.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.access.caller.entity.ApiKey;

import java.util.List;

public interface ApiKeyService extends IService<ApiKey> {
    
    ApiKey createApiKey(Long callerId);

    ApiKey createApiKey(Long callerId, String keyName);
    
    List<ApiKey> listByCaller(Long callerId);
    
    ApiKey getByKey(String apiKey);
    
    boolean validateAndConsumeQuota(String apiKey, long count);
}
