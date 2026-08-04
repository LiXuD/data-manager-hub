package com.dataplatform.access.call.service;

import com.dataplatform.api.Result;
import com.dataplatform.access.connector.service.ConnectorVendorExecutor;
import com.dataplatform.common.adapter.VendorAdapter;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.common.adapter.VendorAdapterFactory;
import com.dataplatform.common.circuitbreaker.CircuitBreakerManager;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.security.pipeline.SecurityDirection;
import com.dataplatform.common.security.pipeline.SecurityStepConfig;
import com.dataplatform.common.security.pipeline.SecurityStepType;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorRuntimeSecurityDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityStepDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * 厂商代理服务
 * 负责调用厂商API并处理熔断和多厂商路由
 */
@Service
public class VendorProxyService {

    private static final Logger log = LoggerFactory.getLogger(VendorProxyService.class);

    @Autowired
    private VendorConfigInternalFeignClient vendorConfigFeignClient;

    @Autowired
    private VendorInternalFeignClient vendorFeignClient;

    @Autowired
    private VendorSecurityInternalFeignClient vendorSecurityFeignClient;

    @Autowired
    private CircuitBreakerManager circuitBreakerManager;

    @Autowired(required = false)
    private ConnectorVendorExecutor connectorVendorExecutor;

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

        VendorConfigDTO runtimeConfig = config;
        try {
            Map<String, Object> result = circuitBreakerManager.executeWithProtection(vendorCode,
                () -> executeConfiguredRuntime(runtimeConfig, vendorCode, dataTypeCode, params));
            result.putIfAbsent("actualVendorId", config.getVendorId());

            if (!Boolean.TRUE.equals(result.get("success"))) {
                if (shouldFallback(result) && config.getFallbackVendorId() != null) {
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
            if (!isPluginMode(config) && config.getFallbackVendorId() != null) {
                return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
            }
            return isPluginMode(config)
                    ? pluginErrorResult("PLUGIN_INTERNAL_ERROR", "连接器执行失败", RequestDeliveryState.MAYBE_SENT)
                    : errorResult("VENDOR_ERROR", e.getMessage());
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

        try {
            Map<String, Object> result = circuitBreakerManager.executeWithProtection(vendorCode,
                () -> executeConfiguredRuntime(config, vendorCode, dataTypeCode, params));
            result.putIfAbsent("actualVendorId", vendorId);

            if (!Boolean.TRUE.equals(result.get("success"))) {
                if (shouldFallback(result) && config.getFallbackVendorId() != null) {
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
            if (!isPluginMode(config) && config.getFallbackVendorId() != null) {
                return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
            }
            return isPluginMode(config)
                    ? pluginErrorResult("PLUGIN_INTERNAL_ERROR", "连接器执行失败", RequestDeliveryState.MAYBE_SENT)
                    : errorResult("VENDOR_ERROR", e.getMessage());
        }
    }

    /**
     * 判断是否应该切换到备用厂商
     */
    private boolean shouldFallback(Map<String, Object> result) {
        String deliveryState = result.get("deliveryState") != null
                ? String.valueOf(result.get("deliveryState")) : null;
        if (deliveryState != null && !RequestDeliveryState.NOT_SENT.name().equals(deliveryState)) {
            return false;
        }
        String errorCode = result.get("errorCode") != null ? String.valueOf(result.get("errorCode")) : null;
        if (errorCode == null) {
            return false;
        }
        return errorCode.startsWith("HTTP_5") ||
               errorCode.equals("VENDOR_ERROR") ||
               errorCode.equals("TIMEOUT") ||
               errorCode.equals("CONNECTION_ERROR") ||
               errorCode.equals("CIRCUIT_BREAKER_OPEN");
    }

    private Map<String, Object> executeConfiguredRuntime(
            VendorConfigDTO config,
            String vendorCode,
            String dataTypeCode,
            Map<String, Object> params) {
        if (!isPluginMode(config)) {
            return executeLegacy(config, vendorCode, dataTypeCode, params);
        }
        if (connectorVendorExecutor == null) {
            return pluginErrorResult("PLUGIN_NOT_READY", "连接器运行时未就绪", RequestDeliveryState.NOT_SENT);
        }
        ConnectorExecutionResult execution = connectorVendorExecutor.execute(
                config, vendorCode, dataTypeCode, params);
        Map<String, Object> pluginResult = toLegacyResult(execution, vendorCode);
        if (!execution.successful() && execution.deliveryState() == RequestDeliveryState.NOT_SENT) {
            Map<String, Object> legacyResult = executeLegacy(config, vendorCode, dataTypeCode, params);
            legacyResult.put("runtimeFallbackFrom", "PLUGIN");
            legacyResult.put("runtimeFallbackErrorCode", execution.errorCode());
            return legacyResult;
        }
        return pluginResult;
    }

    private Map<String, Object> executeLegacy(
            VendorConfigDTO config,
            String vendorCode,
            String dataTypeCode,
            Map<String, Object> params) {
        VendorAdapterConfig adapterConfig = buildAdapterConfig(config, vendorCode, dataTypeCode);
        VendorAdapter adapter = VendorAdapterFactory.getAdapter(vendorCode);
        Map<String, Object> result = adapter.execute(adapterConfig, params);
        result.putIfAbsent("actualVendorCode", vendorCode);
        return result;
    }

    private Map<String, Object> toLegacyResult(ConnectorExecutionResult execution, String vendorCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", execution.successful());
        result.put("data", execution.normalizedData());
        result.put("transportStatus", execution.transportStatus() != null
                ? execution.transportStatus().name() : null);
        result.put("businessStatus", execution.businessStatus() != null
                ? execution.businessStatus().name() : null);
        result.put("errorCategory", execution.errorCategory() != null
                ? execution.errorCategory().name() : null);
        result.put("errorCode", execution.errorCode());
        result.put("errorMsg", execution.safeMessage());
        result.put("billingSignal", execution.billingSignal() != null
                ? execution.billingSignal().name() : null);
        result.put("cacheSignal", execution.cacheSignal() != null
                ? execution.cacheSignal().name() : null);
        result.put("deliveryState", execution.deliveryState() != null
                ? execution.deliveryState().name() : RequestDeliveryState.MAYBE_SENT.name());
        result.put("pluginId", execution.pluginId());
        result.put("pluginVersion", execution.pluginVersion());
        result.put("pipelineVersion", execution.pipelineVersion());
        result.put("snapshotHash", execution.snapshotHash());
        result.put("stageTimings", execution.stageTimings());
        result.put("actualVendorCode", vendorCode);
        return result;
    }

    private boolean isPluginMode(VendorConfigDTO config) {
        return config != null && "PLUGIN".equalsIgnoreCase(config.getRuntimeMode());
    }

    private Map<String, Object> pluginErrorResult(
            String errorCode, String safeMessage, RequestDeliveryState deliveryState) {
        Map<String, Object> result = errorResult(errorCode, safeMessage);
        result.put("errorCategory", errorCode);
        result.put("deliveryState", deliveryState.name());
        return result;
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
        adapterConfig.setAuthType(config.getAuthType());

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

        if (config.getAuthConfig() != null && !config.getAuthConfig().isEmpty()) {
            try {
                Map<String, Object> authConfig = objectMapper.readValue(config.getAuthConfig(),
                        new TypeReference<Map<String, Object>>() {});
                adapterConfig.setAuthConfig(authConfig);
            } catch (Exception e) {
                throw new IllegalArgumentException("解析认证配置失败", e);
            }
        }

        if (config.getId() != null) {
            try {
                Result<VendorRuntimeSecurityDTO> runtimeResult = vendorSecurityFeignClient.getRuntimeSecurity(config.getId());
                VendorRuntimeSecurityDTO runtime = runtimeResult != null ? runtimeResult.getData() : null;
                if (runtime != null) {
                    adapterConfig.setSecuritySteps(runtime.getSteps().stream().map(this::toRuntimeStep).toList());
                    Map<String, String> secrets = new LinkedHashMap<>(runtime.getResolvedSecrets());
                    if (adapterConfig.getSecretKey() != null) {
                        secrets.putIfAbsent("vendor.secretKey", adapterConfig.getSecretKey());
                    }
                    adapterConfig.setResolvedSecrets(secrets);
                }
            } catch (Exception e) {
                throw new IllegalStateException("加载厂商安全流水线失败，已阻止厂商调用: vendor=" + vendorCode, e);
            }
        }

        return adapterConfig;
    }

    private SecurityStepConfig toRuntimeStep(VendorSecurityStepDTO dto) {
        SecurityStepConfig step = new SecurityStepConfig();
        step.setId(dto.getStepKey());
        step.setDirection(SecurityDirection.valueOf(dto.getDirection()));
        step.setStepType(SecurityStepType.valueOf(dto.getStepType()));
        step.setStepName(dto.getStepName());
        step.setSortNo(dto.getSortNo());
        step.setEnabled(dto.getEnabled());
        step.setConfig(dto.getConfig());
        return step;
    }

    private Map<String, Object> errorResult(String errorCode, String errorMsg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("errorCode", errorCode);
        result.put("errorMsg", errorMsg);
        return result;
    }
}
