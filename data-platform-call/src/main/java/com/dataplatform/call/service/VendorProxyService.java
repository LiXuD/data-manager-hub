package com.dataplatform.call.service;

import com.dataplatform.common.adapter.VendorAdapter;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.common.adapter.VendorAdapterFactory;
import com.dataplatform.vendor.entity.VendorConfig;
import com.dataplatform.vendor.service.VendorConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 厂商代理服务
 * 负责调用厂商API并处理熔断
 */
@Service
public class VendorProxyService {

    private static final Logger log = LoggerFactory.getLogger(VendorProxyService.class);

    @Autowired
    private VendorConfigService vendorConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用厂商API
     *
     * @param vendorCode   厂商编码
     * @param dataTypeCode 数据类型编码
     * @param params       请求参数
     * @return 响应结果
     */
    public Map<String, Object> callVendor(String vendorCode, String dataTypeCode,
                                           Map<String, Object> params) {
        // 1. 获取厂商配置
        VendorConfig config = vendorConfigService.getByVendorCodeAndDataTypeCode(vendorCode, dataTypeCode);
        if (config == null) {
            return errorResult("CONFIG_NOT_FOUND", "厂商配置不存在: " + vendorCode + "/" + dataTypeCode);
        }

        // 2. 检查厂商状态
        if (!"active".equals(config.getStatus())) {
            return errorResult("VENDOR_INACTIVE", "厂商已禁用: " + vendorCode);
        }

        // 3. 构建适配器配置
        VendorAdapterConfig adapterConfig = buildAdapterConfig(config, vendorCode, dataTypeCode);

        // 4. 获取适配器并执行
        VendorAdapter adapter = VendorAdapterFactory.getAdapter(vendorCode);

        try {
            Map<String, Object> result = adapter.execute(adapterConfig, params);
            log.info("厂商调用成功: vendor={}, type={}", vendorCode, dataTypeCode);
            return result;
        } catch (Exception e) {
            log.error("厂商调用失败: vendor={}, error={}", vendorCode, e.getMessage(), e);
            return errorResult("VENDOR_ERROR", e.getMessage());
        }
    }

    /**
     * 构建适配器配置
     */
    private VendorAdapterConfig buildAdapterConfig(VendorConfig config, String vendorCode, String dataTypeCode) {
        VendorAdapterConfig adapterConfig = new VendorAdapterConfig();
        adapterConfig.setVendorCode(vendorCode);
        adapterConfig.setDataTypeCode(dataTypeCode);
        adapterConfig.setApiUrl(config.getApiUrl());
        adapterConfig.setMethod(config.getMethod());
        adapterConfig.setTimeout(config.getTimeout());
        adapterConfig.setRetryCount(config.getRetryCount());
        adapterConfig.setRequestTemplate(config.getRequestTemplate());
        adapterConfig.setResponseMapping(config.getResponseMapping());
        adapterConfig.setSignType(config.getSignType());

        // 解析请求头配置
        if (config.getHeaderConfig() != null && !config.getHeaderConfig().isEmpty()) {
            try {
                Map<String, String> headers = objectMapper.readValue(config.getHeaderConfig(),
                    new TypeReference<Map<String, String>>() {});
                adapterConfig.setHeaders(headers);
            } catch (Exception e) {
                log.warn("解析请求头配置失败: {}", e.getMessage());
            }
        }

        return adapterConfig;
    }

    /**
     * 构建错误结果
     */
    private Map<String, Object> errorResult(String errorCode, String errorMsg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("errorCode", errorCode);
        result.put("errorMsg", errorMsg);
        return result;
    }
}
