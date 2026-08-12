package com.dataplatform.access.call.service;

import com.dataplatform.api.Result;
import com.dataplatform.access.connector.service.ConnectorVendorExecutor;
import com.dataplatform.common.circuitbreaker.CircuitBreakerManager;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ConnectorErrorPolicy;
import com.dataplatform.plugin.spi.ErrorCategory;
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
    private CircuitBreakerManager circuitBreakerManager;

    @Autowired
    private ConnectorVendorExecutor connectorVendorExecutor;

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
     * Explicit OpenAPI route. The fallback is a single exact configuration and
     * its own fallbackVendorId is deliberately ignored.
     */
    public Map<String, Object> callVendor(String primaryVendorCode, String fallbackVendorCode,
                                           String dataTypeCode, Map<String, Object> params,
                                           VendorConfigDTO primaryConfig, VendorConfigDTO fallbackConfig) {
        Map<String, Object> primaryResult = executeExplicitConfig(
                primaryConfig, primaryVendorCode, dataTypeCode, params);
        if (Boolean.TRUE.equals(primaryResult.get("success"))
                || !shouldFallback(primaryResult)
                || fallbackConfig == null) {
            return primaryResult;
        }

        String resolvedFallbackCode = normalize(fallbackVendorCode);
        if (resolvedFallbackCode == null) {
            resolvedFallbackCode = resolveVendorCode(fallbackConfig.getVendorId());
        }
        if (resolvedFallbackCode == null) {
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "FALLBACK_UNAVAILABLE", "主厂商和备用厂商均不可用", null);
        }

        Map<String, Object> fallbackResult = executeExplicitConfig(
                fallbackConfig, resolvedFallbackCode, dataTypeCode, params);
        fallbackResult.put("fallbackFrom", primaryVendorCode);
        return fallbackResult;
    }

    private Map<String, Object> executeExplicitConfig(VendorConfigDTO config, String vendorCode,
                                                      String dataTypeCode, Map<String, Object> params) {
        if (config == null || config.getVendorId() == null
                || !StatusConstants.ACTIVE.equals(config.getStatus())
                || normalize(vendorCode) == null) {
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "CONFIG_NOT_READY", "显式厂商路由配置不可用", RequestDeliveryState.NOT_SENT);
        }
        try {
            Map<String, Object> result = circuitBreakerManager.executeWithProtection(vendorCode,
                    () -> executeConfiguredRuntime(config, vendorCode, dataTypeCode, params),
                    this::isCircuitFailure);
            result.putIfAbsent("actualVendorId", config.getVendorId());
            result.putIfAbsent("actualVendorCode", vendorCode);
            return result;
        } catch (CallNotPermittedException exception) {
            return pluginErrorResult(ErrorCategory.TRANSPORT_CONNECTION_ERROR,
                    "CIRCUIT_BREAKER_OPEN", "厂商服务暂时不可用，请稍后重试",
                    RequestDeliveryState.NOT_SENT);
        } catch (Exception exception) {
            log.error("显式厂商调用失败: vendor={}, error={}", vendorCode, exception.getMessage());
            return pluginErrorResult(ErrorCategory.PLUGIN_INTERNAL_ERROR,
                    "PLUGIN_INTERNAL_ERROR", "连接器执行失败", RequestDeliveryState.MAYBE_SENT);
        }
    }

    private String resolveVendorCode(Long vendorId) {
        if (vendorId == null) {
            return null;
        }
        Result<VendorInfoDTO> result = vendorFeignClient.getById(vendorId);
        VendorInfoDTO vendor = result != null ? result.getData() : null;
        if (vendor == null || !StatusConstants.ACTIVE.equals(vendor.getStatus())) {
            return null;
        }
        return normalize(vendor.getVendorCode());
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
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
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "CIRCULAR_ROUTING", "厂商路由配置存在循环", null);
        }
        triedVendors.add(vendorCode);

        VendorConfigDTO config = preFetchedConfig;
        if (config == null) {
            Result<VendorConfigDTO> configResult = vendorConfigFeignClient.getByVendorCodeAndDataTypeCode(vendorCode, dataTypeCode);
            config = configResult != null ? configResult.getData() : null;
        }
        if (config == null) {
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "CONFIG_NOT_FOUND", "厂商配置不存在: " + vendorCode + "/" + dataTypeCode, null);
        }

        if (!StatusConstants.ACTIVE.equals(config.getStatus())) {
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "VENDOR_INACTIVE", "厂商已禁用: " + vendorCode, null);
        }

        VendorConfigDTO runtimeConfig = config;
        try {
            Map<String, Object> result = circuitBreakerManager.executeWithProtection(vendorCode,
                () -> executeConfiguredRuntime(runtimeConfig, vendorCode, dataTypeCode, params),
                this::isCircuitFailure);
            result.putIfAbsent("actualVendorId", config.getVendorId());

            if (!Boolean.TRUE.equals(result.get("success"))) {
                if (shouldFallback(result) && config.getFallbackVendorId() != null) {
                    return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
                }
            }

            return result;

        } catch (CallNotPermittedException e) {
            log.warn("熔断器打开，尝试备用厂商: vendor={}", vendorCode);
            Map<String, Object> rejected = pluginErrorResult(
                    ErrorCategory.TRANSPORT_CONNECTION_ERROR,
                    "CIRCUIT_BREAKER_OPEN", "厂商服务暂时不可用，请稍后重试",
                    RequestDeliveryState.NOT_SENT);
            if (shouldFallback(rejected) && config.getFallbackVendorId() != null) {
                return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
            }
            return rejected;
        } catch (Exception e) {
            log.error("厂商调用失败: vendor={}, error={}", vendorCode, e.getMessage());
            return pluginErrorResult(ErrorCategory.PLUGIN_INTERNAL_ERROR,
                    "PLUGIN_INTERNAL_ERROR", "连接器执行失败", RequestDeliveryState.MAYBE_SENT);
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
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "FALLBACK_UNAVAILABLE", "主厂商和备用厂商均不可用", null);
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
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "CIRCULAR_ROUTING", "厂商路由配置存在循环", null);
        }
        triedVendors.add(vendorCode);

        Result<VendorConfigDTO> configResult = vendorConfigFeignClient.getByVendorIdAndDataTypeCode(vendorId, dataTypeCode);
        VendorConfigDTO config = configResult != null ? configResult.getData() : null;
        if (config == null) {
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "CONFIG_NOT_FOUND", "厂商配置不存在: " + vendorCode + "/" + dataTypeCode, null);
        }

        if (!StatusConstants.ACTIVE.equals(config.getStatus())) {
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "VENDOR_INACTIVE", "厂商已禁用: " + vendorCode, null);
        }

        try {
            Map<String, Object> result = circuitBreakerManager.executeWithProtection(vendorCode,
                () -> executeConfiguredRuntime(config, vendorCode, dataTypeCode, params),
                this::isCircuitFailure);
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
            Map<String, Object> rejected = pluginErrorResult(
                    ErrorCategory.TRANSPORT_CONNECTION_ERROR,
                    "CIRCUIT_BREAKER_OPEN", "厂商服务暂时不可用，请稍后重试",
                    RequestDeliveryState.NOT_SENT);
            if (shouldFallback(rejected) && config.getFallbackVendorId() != null) {
                return tryFallbackVendor(config.getFallbackVendorId(), dataTypeCode, params, triedVendors, vendorCode);
            }
            return rejected;
        } catch (Exception e) {
            log.error("厂商调用失败: vendor={}, error={}", vendorCode, e.getMessage());
            return pluginErrorResult(ErrorCategory.PLUGIN_INTERNAL_ERROR,
                    "PLUGIN_INTERNAL_ERROR", "连接器执行失败", RequestDeliveryState.MAYBE_SENT);
        }
    }

    /**
     * 判断是否应该切换到备用厂商
     */
    private boolean shouldFallback(Map<String, Object> result) {
        ErrorCategory category = errorCategory(result);
        if (category == null) {
            return false;
        }
        RequestDeliveryState delivery = deliveryState(result.get("deliveryState"));
        return ConnectorErrorPolicy.forCategory(category).canFallback(delivery);
    }

    private boolean isCircuitFailure(Map<String, Object> result) {
        ErrorCategory category = errorCategory(result);
        return category != null && ConnectorErrorPolicy.forCategory(category).circuitFailure();
    }

    private Map<String, Object> executeConfiguredRuntime(
            VendorConfigDTO config,
            String vendorCode,
            String dataTypeCode,
            Map<String, Object> params) {
        if (!isPluginMode(config)) {
            return pluginErrorResult(ErrorCategory.CONFIGURATION_ERROR,
                    "PLUGIN_RUNTIME_REQUIRED", "厂商配置尚未发布连接器版本",
                    RequestDeliveryState.NOT_SENT);
        }
        ConnectorExecutionResult execution = connectorVendorExecutor.execute(
                config, vendorCode, dataTypeCode, params);
        return toCallResult(execution, vendorCode);
    }

    private Map<String, Object> toCallResult(ConnectorExecutionResult execution, String vendorCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        ConnectorErrorPolicy errorPolicy = execution.errorCategory() != null
                ? ConnectorErrorPolicy.forCategory(execution.errorCategory()) : null;
        result.put("success", execution.successful());
        result.put("data", execution.normalizedData());
        result.put("transportStatus", execution.transportStatus() != null
                ? execution.transportStatus().name() : null);
        result.put("businessStatus", execution.businessStatus() != null
                ? execution.businessStatus().name() : null);
        result.put("errorCategory", execution.errorCategory() != null
                ? execution.errorCategory().name() : null);
        result.put("connectorErrorCode", execution.errorCode());
        result.put("errorCode", errorPolicy != null ? errorPolicy.externalCode() : null);
        result.put("errorMsg", execution.safeMessage());
        RequestDeliveryState delivery = errorPolicy != null
                ? errorPolicy.deliveryState(execution.deliveryState())
                : execution.deliveryState() != null ? execution.deliveryState()
                : execution.successful() ? RequestDeliveryState.SENT : RequestDeliveryState.MAYBE_SENT;
        result.put("billingSignal", errorPolicy != null
                ? errorPolicy.billingSignal(execution.billingSignal(), delivery).name()
                : execution.billingSignal() != null ? execution.billingSignal().name() : null);
        result.put("cacheSignal", errorPolicy != null
                ? errorPolicy.cacheSignal(execution.cacheSignal()).name()
                : execution.cacheSignal() != null ? execution.cacheSignal().name() : null);
        result.put("deliveryState", delivery.name());
        result.put("pluginId", execution.pluginId());
        result.put("pluginVersion", execution.pluginVersion());
        result.put("pipelineVersion", execution.pipelineVersion());
        result.put("snapshotHash", execution.snapshotHash());
        result.put("hashAlgorithm", execution.hashAlgorithm());
        result.put("integrityHash", execution.integrityHash());
        result.put("stageTimings", execution.stageTimings());
        result.put("actualVendorCode", vendorCode);
        return result;
    }

    private boolean isPluginMode(VendorConfigDTO config) {
        return config != null && "PLUGIN".equalsIgnoreCase(config.getRuntimeMode());
    }

    private Map<String, Object> pluginErrorResult(
            ErrorCategory category, String errorCode, String safeMessage,
            RequestDeliveryState deliveryState) {
        ConnectorErrorPolicy policy = ConnectorErrorPolicy.forCategory(category);
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("errorCode", policy.externalCode());
        result.put("errorMsg", safeMessage);
        result.put("connectorErrorCode", errorCode);
        result.put("errorCategory", category.name());
        RequestDeliveryState effectiveDelivery = policy.deliveryState(deliveryState);
        result.put("deliveryState", effectiveDelivery.name());
        result.put("billingSignal", policy.billingSignal(null, effectiveDelivery).name());
        result.put("cacheSignal", policy.cacheSignal(null).name());
        return result;
    }

    private ErrorCategory errorCategory(Map<String, Object> result) {
        Object raw = result != null ? result.get("errorCategory") : null;
        if (raw == null) return null;
        try {
            return ErrorCategory.valueOf(String.valueOf(raw));
        } catch (IllegalArgumentException ignored) {
            return ErrorCategory.PLUGIN_INTERNAL_ERROR;
        }
    }

    private RequestDeliveryState deliveryState(Object raw) {
        if (raw == null) return null;
        try {
            return RequestDeliveryState.valueOf(String.valueOf(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

}
