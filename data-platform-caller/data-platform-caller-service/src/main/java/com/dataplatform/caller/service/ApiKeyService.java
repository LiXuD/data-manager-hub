package com.dataplatform.caller.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.caller.entity.ApiKey;

import java.util.List;

public interface ApiKeyService extends IService<ApiKey> {
    
    ApiKey createApiKey(Long callerId);
    
    List<ApiKey> listByCaller(Long callerId);
    
    ApiKey getByKey(String apiKey);
    
    boolean validateAndConsumeQuota(String apiKey, long count);
}
