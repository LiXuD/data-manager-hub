package com.dataplatform.access.caller.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.access.caller.entity.ApiKey;

import java.util.List;

/**
 * 访问域调用方的 Api Key Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
public interface ApiKeyService extends IService<ApiKey> {
    
    ApiKey createApiKey(Long callerId);

    ApiKey createApiKey(Long callerId, String keyName);
    
    List<ApiKey> listByCaller(Long callerId);
    
    ApiKey getByKey(String apiKey);
    
    boolean validateAndConsumeQuota(String apiKey, long count);
}
