package com.dataplatform.masterdata.vendor.service;

import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class VendorAdapterConfigAssembler {

    private final ObjectMapper objectMapper;

    public VendorAdapterConfigAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public VendorAdapterConfig build(VendorConfig config, VendorInfo vendor) {
        VendorAdapterConfig adapterConfig = new VendorAdapterConfig();
        adapterConfig.setVendorCode(vendor.getVendorCode());
        adapterConfig.setApiUrl(config.getApiUrl());
        adapterConfig.setMethod(config.getMethod());
        adapterConfig.setTimeout(config.getTimeout());
        adapterConfig.setRetryCount(config.getRetryCount());
        adapterConfig.setSignType(config.getSignType());
        adapterConfig.setSecretKey(vendor.getSecretKey());
        adapterConfig.setRequestTemplate(config.getRequestTemplate());
        adapterConfig.setResponseMapping(config.getResponseMapping());
        adapterConfig.setAuthType(config.getAuthType());
        adapterConfig.setHeaders(read(config.getHeaderConfig(), new TypeReference<Map<String, String>>() { }, "请求头"));
        adapterConfig.setAuthConfig(read(config.getAuthConfig(), new TypeReference<Map<String, Object>>() { }, "认证"));
        return adapterConfig;
    }

    private <T> T read(String json, TypeReference<T> type, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("厂商" + fieldName + "配置格式错误", e);
        }
    }
}
