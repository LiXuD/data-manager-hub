package com.dataplatform.access.caller.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.caller.entity.ApiKeyProduct;
import com.dataplatform.access.caller.mapper.ApiKeyProductMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyProductService extends ServiceImpl<ApiKeyProductMapper, ApiKeyProduct> {

    public boolean hasProductPermission(Long apiKeyId, Long productId) {
        if (apiKeyId == null || productId == null) {
            return false;
        }
        LambdaQueryWrapper<ApiKeyProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyProduct::getApiKeyId, apiKeyId)
                .eq(ApiKeyProduct::getProductId, productId);
        return count(wrapper) > 0;
    }

    public List<Long> getProductIdsByApiKeyId(Long apiKeyId) {
        LambdaQueryWrapper<ApiKeyProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyProduct::getApiKeyId, apiKeyId);
        return list(wrapper).stream()
                .map(ApiKeyProduct::getProductId)
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignProducts(Long apiKeyId, List<Long> productIds) {
        LambdaQueryWrapper<ApiKeyProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyProduct::getApiKeyId, apiKeyId);
        remove(wrapper);

        if (productIds != null && !productIds.isEmpty()) {
            List<ApiKeyProduct> records = productIds.stream()
                    .map(productId -> {
                        ApiKeyProduct record = new ApiKeyProduct();
                        record.setApiKeyId(apiKeyId);
                        record.setProductId(productId);
                        return record;
                    })
                    .collect(Collectors.toList());
            saveBatch(records);
        }
    }
}
