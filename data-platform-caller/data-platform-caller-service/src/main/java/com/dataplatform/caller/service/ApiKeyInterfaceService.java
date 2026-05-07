package com.dataplatform.caller.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.caller.entity.ApiKeyInterface;
import com.dataplatform.caller.mapper.ApiKeyInterfaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApiKeyInterfaceService extends ServiceImpl<ApiKeyInterfaceMapper, ApiKeyInterface> {

    public List<Long> getInterfaceIdsByApiKeyId(Long apiKeyId) {
        LambdaQueryWrapper<ApiKeyInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyInterface::getApiKeyId, apiKeyId);
        return list(wrapper).stream()
                .map(ApiKeyInterface::getInterfaceId)
                .collect(Collectors.toList());
    }

    public boolean hasInterfacePermission(Long apiKeyId, Long interfaceId) {
        LambdaQueryWrapper<ApiKeyInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyInterface::getApiKeyId, apiKeyId)
                .eq(ApiKeyInterface::getInterfaceId, interfaceId);
        return count(wrapper) > 0;
    }

    @Transactional
    public void assignInterfaces(Long apiKeyId, List<Long> interfaceIds) {
        // 删除旧的授权
        LambdaQueryWrapper<ApiKeyInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyInterface::getApiKeyId, apiKeyId);
        remove(wrapper);

        // 新增授权
        if (interfaceIds != null && !interfaceIds.isEmpty()) {
            List<ApiKeyInterface> records = interfaceIds.stream()
                    .map(id -> {
                        ApiKeyInterface record = new ApiKeyInterface();
                        record.setApiKeyId(apiKeyId);
                        record.setInterfaceId(id);
                        return record;
                    })
                    .collect(Collectors.toList());
            saveBatch(records);
        }
    }
}
