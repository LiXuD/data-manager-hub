package com.dataplatform.access.call.service;

import com.dataplatform.api.Result;
import com.dataplatform.common.adapter.VendorAdapter;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.common.adapter.VendorAdapterFactory;
import com.dataplatform.common.circuitbreaker.CircuitBreakerManager;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorFeignClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 厂商代理服务
 * 负责调用厂商API并处理熔断和多厂商路由
 */
@Service
public class VendorProxyService {

    private static final Logger log = LoggerFactory.getLogger(VendorProxyService.class);

    @Autowired
    private VendorConfigFeignClient vendorConfigFeignClient;

    @Autowired
    private VendorFeignClient vendorFeignClient;

    @Autowired
    private CircuitBreakerManager circuitBreakerManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用厂商API (支持多厂商路由)
     */
    public Map<String, Object> callVendor(String vendorCode, String dataTypeCode,
                                           Map<String, Object> params) {
        return callVendor(vendorCode, dataTypeCode, params, null);
    }

    /**
     * 调用厂商API (支持多厂商路由，接受预获取的配置)
     */
    public Map<String, Object> callVendor(String vendorCode, String dataTypeCode,
                                           Map<String, Object> params,
                                           VendorConfigDTO preFetchedConfig) {
        Set<String> triedVendors = new HashSet<>();
        return callVendorWithFallback(vendorCode, dataTypeCode, params, triedVendors, preFetchedConfig);
    }

    /**
     * 递归调用厂商API，支持主备切换
     */
    private Map<String, Object> callVendorWithFallback(String vendorCode, String dataTypeCode,
                                                        Map<String, Object> params,
                                                        Set<String> triedVendors,
                                                        VendorConfigDTO preFetchedConfig) {
        if (triedVendors.contains(vendorCode)) {
            log.warn("检测到厂商循环调用，终止: vendor={}", vendorCode);
            return errorResult("CIRCULAR_ROUTING", "厂商路由配置存在循环");
        }
        triedVendors.add(vendorCode);

        VendorConfigDTO config = preFetchedConfig;
        if (config == null) {
            Result<VendorConfigDTO> configResult = vendorConfigFeignClient.getByVendorCodeAndDataTypeCode(vendorCode, dataTypeCode);
            config = configResult != null ? configResult.getData() : null;
        }
        if (config == null) {
            return errorResult("CONFIG_NOT_FOUND", "厂商配置不存在: " + vendorCode + "/" + dataTypeCode);
        }

        if (!StatusConstants.ACTIVE.equals(config.getStatus())) {
            return errorResult("VENDOR_INACTIVE", "厂商已禁用: " + vendorCode);
        }

        VendorAdapterConfig adapterConfig = buildAdapterConfig(config, vendorCode, dataTypeCode);
        VendorAdapter adapter = VendorAdapterFactory.getAdapter(vendorCode);

        try {
            Map<String, Object> result = circuitBreakerManager.executeWithProtection(vendorCode,
                () -> adapter.execute(adapterConfig, params));

            if (!Boolean.TRUE.equals(result.get("success"))) {
                String errorCode = (String) result.get("errorCode");
                if (shouldFallback(errorCode) && config.getFallbackVendorId() != null) {
                    return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
                }
            }

            return result;

        } catch (CallNotPermittedException e) {
            log.warn("熔断器打开，尝试备用厂商: vendor={}", vendorCode);
            if (config.getFallbackVendorId() != null) {
                return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
            }
            return errorResult("CIRCUIT_BREAKER_OPEN", "厂商服务暂时不可用，请稍后重试");
        } catch (Exception e) {
            log.error("厂商调用失败: vendor={}, error={}", vendorCode, e.getMessage());
            if (config.getFallbackVendorId() != null) {
                return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
            }
            return errorResult("VENDOR_ERROR", e.getMessage());
        }
    }

    /**
     * 尝试备用厂商
     */
    private Map<String, Object> tryFallbackVendor(Long fallbackVendorId, String dataTypeCode,
                                                   Map<String, Object> params,
                                                   Set<String> triedVendors,
                                                   String originalVendorCode) {
        Result<VendorInfoDTO> vendorResult = vendorFeignClient.getById(fallbackVendorId);
        VendorInfoDTO fallbackVendor = vendorResult != null ? vendorResult.getData() : null;
        if (fallbackVendor == null || !StatusConstants.ACTIVE.equals(fallbackVendor.getStatus())) {
            log.warn("备用厂商不可用: vendorId={}", fallbackVendorId);
            return errorResult("FALLBACK_UNAVAILABLE", "主厂商和备用厂商均不可用");
        }

        String fallbackVendorCode = fallbackVendor.getVendorCode();
        log.info("切换到备用厂商: {} -> {}", originalVendorCode, fallbackVendorCode);

        return callVendorWithFallbackById(fallbackVendorId, fallbackVendorCode, dataTypeCode, params, triedVendors, originalVendorCode);
    }

    /**
     * 通过 vendorId 调用厂商API
     */
    private Map<String, Object> callVendorWithFallbackById(Long vendorId, String vendorCode, String dataTypeCode,
                                                            Map<String, Object> params,
                                                            Set<String> triedVendors,
                                                            String originalVendorCode) {
        if (triedVendors.contains(vendorCode)) {
            log.warn("检测到厂商循环调用，终止: vendor={}", vendorCode);
            return errorResult("CIRCULAR_ROUTING", "厂商路由配置存在循环");
        }
        triedVendors.add(vendorCode);

        Result<VendorConfigDTO> configResult = vendorConfigFeignClient.getByVendorIdAndDataTypeCode(vendorId, dataTypeCode);
        VendorConfigDTO config = configResult != null ? configResult.getData() : null;
        if (config == null) {
            return errorResult("CONFIG_NOT_FOUND", "厂商配置不存在: " + vendorCode + "/" + dataTypeCode);
        }

        if (!StatusConstants.ACTIVE.equals(config.getStatus())) {
            return errorResult("VENDOR_INACTIVE", "厂商已禁用: " + vendorCode);
        }

        VendorAdapterConfig adapterConfig = buildAdapterConfig(config, vendorCode, dataTypeCode);
        VendorAdapter adapter = VendorAdapterFactory.getAdapter(vendorCode);

        try {
            Map<String, Object> result = circuitBreakerManager.executeWithProtection(vendorCode,
                () -> adapter.execute(adapterConfig, params));

            if (!Boolean.TRUE.equals(result.get("success"))) {
                String errorCode = (String) result.get("errorCode");
                if (shouldFallback(errorCode) && config.getFallbackVendorId() != null) {
                    return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
                }
            }

            if (originalVendorCode != null) {
                result.put("fallbackFrom", originalVendorCode);
            }
            return result;

        } catch (CallNotPermittedException e) {
            log.warn("熔断器打开，尝试备用厂商: vendor={}", vendorCode);
            if (config.getFallbackVendorId() != null) {
                return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
            }
            return errorResult("CIRCUIT_BREAKER_OPEN", "厂商服务暂时不可用，请稍后重试");
        } catch (Exception e) {
            log.error("厂商调用失败: vendor={}, error={}", vendorCode, e.getMessage());
            if (config.getFallbackVendorId() != null) {
                return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
            }
            return errorResult("VENDOR_ERROR", e.getMessage());
        }
    }

    /**
     * 判断是否应该切换到备用厂商
     */
    private boolean shouldFallback(String errorCode) {
        if (errorCode == null) {
            return false;
        }
        return errorCode.startsWith("HTTP_5") ||
               errorCode.equals("VENDOR_ERROR") ||
               errorCode.equals("TIMEOUT") ||
               errorCode.equals("CONNECTION_ERROR") ||
               errorCode.equals("CIRCUIT_BREAKER_OPEN");
    }

    /**
     * 构建适配器配置
     */
    private VendorAdapterConfig buildAdapterConfig(VendorConfigDTO config, String vendorCode, String dataTypeCode) {
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

        Result<String> secretKeyResult = vendorConfigFeignClient.getSecretKey(vendorCode);
        adapterConfig.setSecretKey(secretKeyResult != null ? secretKeyResult.getData() : null);

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

    private Map<String, Object> errorResult(String errorCode, String errorMsg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("errorCode", errorCode);
        result.put("errorMsg", errorMsg);
        return result;
    }
}
