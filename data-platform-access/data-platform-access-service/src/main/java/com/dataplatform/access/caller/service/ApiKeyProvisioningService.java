package com.dataplatform.access.caller.service;

import com.dataplatform.access.caller.entity.ApiKey;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在同一事务中创建 API Key 并写入产品授权。
 */
@Service
public class ApiKeyProvisioningService {

    private final ApiKeyService apiKeyService;
    private final ApiKeyProductService apiKeyProductService;

    public ApiKeyProvisioningService(ApiKeyService apiKeyService,
                                     ApiKeyProductService apiKeyProductService) {
        this.apiKeyService = apiKeyService;
        this.apiKeyProductService = apiKeyProductService;
    }

    @Transactional
    public ApiKey create(Long callerId, String keyName, List<Long> productIds) {
        ApiKey apiKey = apiKeyService.createApiKey(callerId, keyName);
        apiKeyProductService.assignProducts(apiKey.getId(), productIds);
        return apiKey;
    }
}
