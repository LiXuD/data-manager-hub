package com.dataplatform.access.call.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.dataplatform.api.Result;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.api.dto.BillingChargeRespDTO;
import com.dataplatform.billing.api.dto.BillingAdditionalPlanDTO;
import com.dataplatform.billing.api.dto.BillingMeteringPolicyDTO;
import com.dataplatform.billing.api.feign.BillingInternalFeignClient;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.access.call.service.VendorProxyService;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.plugin.spi.ConnectorErrorPolicy;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 访问域数据调用的 Open Api Query Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class OpenApiQueryService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiQueryService.class);

    private static final String DEFAULT_API_VERSION = "v1";
    private static final String MASKED_VALUE = "***MASKED***";

    private final CallRecordService callRecordService;
    private final CallRecordEventPublisher callRecordEventPublisher;
    private final VendorProxyService vendorProxyService;
    private final BillingInternalFeignClient billingFeignClient;
    private final BillingFactExtractor billingFactExtractor;
    private final ObjectMapper objectMapper;

    public OpenApiQueryService(CallRecordService callRecordService,
                               CallRecordEventPublisher callRecordEventPublisher,
                               VendorProxyService vendorProxyService,
                               BillingInternalFeignClient billingFeignClient,
                               BillingFactExtractor billingFactExtractor) {
        this.callRecordService = callRecordService;
        this.callRecordEventPublisher = callRecordEventPublisher;
        this.vendorProxyService = vendorProxyService;
        this.billingFeignClient = billingFeignClient;
        this.billingFactExtractor = billingFactExtractor;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public OpenApiQueryRespVO query(OpenApiCallContext context) {
        if (context == null) {
            throw OpenApiQueryException.badRequest(
                    "OPENAPI_CONTEXT_INVALID", "调用请求上下文不能为空");
        }
        LocalDateTime requestTime = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        String platformRequestId = generateRequestId();
        String requestHash = buildRequestHash(context);
        boolean useCache = Boolean.TRUE.equals(context.getUseCache());
        if (useCache && (context.getCacheDays() == null || context.getCacheDays() <= 0)) {
            throw OpenApiQueryException.badRequest(
                    "OPENAPI_CACHE_POLICY_INVALID", "useCache=true时cacheDays必须大于0");
        }
        if (useCache) {
            CallRecord cachedRecord;
            try {
                cachedRecord = callRecordService.findLatestReusableCache(
                        context.getApiCode(),
                        requestHash,
                        context.getTenantId(),
                        context.getCallerId(),
                        requestTime.minusDays(context.getCacheDays()),
                        context.getCacheScope());
            } catch (RuntimeException exception) {
                throw OpenApiQueryException.serviceUnavailable(
                        "OPENAPI_CACHE_UNAVAILABLE", "调用缓存服务暂不可用");
            }
            if (cachedRecord != null && !Boolean.FALSE.equals(cachedRecord.getResponseContractValid())) {
                LocalDateTime responseTime = LocalDateTime.now();
                long duration = System.currentTimeMillis() - startTime;
                Map<String, Object> cachedResult = readResponseData(cachedRecord.getResponseData());
                if (cachedResult != null && cachedResult.get("success") instanceof Boolean) {
                    cachedResult.putIfAbsent("actualVendorId", cachedRecord.getVendorId());
                    cachedResult.putIfAbsent("actualVendorCode", cachedRecord.getVendorCode());
                    ResponseContractEvaluation contractEvaluation = evaluateResponseContract(context, cachedResult);
                    if (contractEvaluation.valid()) {
                        cachedResult.put("responseContractValid", true);
                        BillingMeteringPolicyDTO meteringPolicy = resolveMeteringPolicy(
                                actualVendorCode(context, cachedResult), context.getApiCode(), requestTime);
                        BigDecimal cost = charge(context, meteringPolicy, platformRequestId, duration,
                                requestTime, true, true, cachedResult,
                                cachedRecord.getPluginId(), cachedRecord.getPluginVersion(),
                                cachedRecord.getPipelineVersion(), cachedRecord.getSnapshotHash(),
                                cachedRecord.getHashAlgorithm(), cachedRecord.getIntegrityHash());
                        CallRecord record = buildRecord(context, platformRequestId, requestHash, cachedResult,
                                true, duration, cost, true, cachedRecord.getId(), requestTime, responseTime);
                        copyConnectorTrace(cachedRecord, record);
                        callRecordEventPublisher.publish(record);
                        return buildResponse(context, platformRequestId, cachedResult, true,
                                cachedRecord.getId(), requestTime, responseTime, duration, cost);
                    }
                }
            }
        }

        Map<String, Object> vendorResult;
        try {
            if (context.getPrimaryVendorConfig() != null) {
                vendorResult = vendorProxyService.callVendor(
                        context.getVendorCode(),
                        context.getFallbackVendorCode(),
                        context.getDataTypeCode(),
                        context.getParams(),
                        context.getPrimaryVendorConfig(),
                        context.getFallbackVendorConfig(),
                        platformRequestId);
            } else {
                vendorResult = vendorProxyService.callVendor(
                        context.getVendorCode(),
                        context.getDataTypeCode(),
                        context.getParams(),
                        context.getVendorConfig(),
                        platformRequestId);
            }
        } catch (OpenApiQueryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_VENDOR_UNAVAILABLE", "厂商调用服务暂不可用");
        }
        if (vendorResult == null) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_VENDOR_UNAVAILABLE", "厂商调用服务未返回有效结果");
        }
        vendorResult = new LinkedHashMap<>(vendorResult);

        LocalDateTime responseTime = LocalDateTime.now();
        long duration = System.currentTimeMillis() - startTime;
        if (Boolean.TRUE.equals(vendorResult.get("success"))) {
            ResponseContractEvaluation contractEvaluation = evaluateResponseContract(context, vendorResult);
            if (contractEvaluation.valid()) {
                vendorResult.put("responseContractValid", true);
            } else {
                applyContractViolation(vendorResult, contractEvaluation.errors());
            }
        }
        boolean success = Boolean.TRUE.equals(vendorResult.get("success"));
        boolean billingEligible = "ELIGIBLE".equals(String.valueOf(vendorResult.get("billingSignal")));
        BigDecimal cost = BigDecimal.ZERO;
        if (billingEligible) {
            BillingMeteringPolicyDTO meteringPolicy = resolveMeteringPolicy(
                    actualVendorCode(context, vendorResult), context.getApiCode(), requestTime);
            cost = charge(context, meteringPolicy, platformRequestId, duration,
                    requestTime, success, false, vendorResult,
                    stringValue(vendorResult.get("pluginId")), stringValue(vendorResult.get("pluginVersion")),
                    integerValue(vendorResult.get("pipelineVersion")), stringValue(vendorResult.get("snapshotHash")),
                    stringValue(vendorResult.get("hashAlgorithm")), stringValue(vendorResult.get("integrityHash")));
        }
        vendorResult.put("requestId", platformRequestId);
        vendorResult.put("cached", false);
        vendorResult.put("latency", duration);

        CallRecord record = buildRecord(context, platformRequestId, requestHash, vendorResult,
                success, duration, cost, false, null, requestTime, responseTime);
        callRecordEventPublisher.publish(record);
        return buildResponse(context, platformRequestId, vendorResult, false,
                null, requestTime, responseTime, duration, cost);
    }

    private BillingMeteringPolicyDTO resolveMeteringPolicy(String vendorCode, String apiCode,
                                                           LocalDateTime callTime) {
        Result<BillingMeteringPolicyDTO> result;
        try {
            result = billingFeignClient.getMeteringPolicy(vendorCode, apiCode, callTime);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_BILLING_UNAVAILABLE", "计费服务暂不可用");
        }
        BillingMeteringPolicyDTO policy = result != null ? result.getData() : null;
        if (result == null || !Integer.valueOf(200).equals(result.getCode())
                || policy == null || policy.getPlanId() == null) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_BILLING_POLICY_UNAVAILABLE", "计费策略暂不可用");
        }
        return policy;
    }

    private BigDecimal charge(OpenApiCallContext context, BillingMeteringPolicyDTO policy,
                              String requestId, long latencyMs, LocalDateTime callTime,
                              boolean success, boolean cached, Map<String, Object> result,
                              String pluginId, String pluginVersion,
                              Integer pipelineVersion, String snapshotHash,
                              String hashAlgorithm, String integrityHash) {
        BillingChargeReqDTO request = new BillingChargeReqDTO();
        request.setRequestId(requestId);
        request.setPlanId(policy.getPlanId());
        request.setPlanVersion(policy.getPlanVersion());
        request.setPolicyHash(policy.getPolicyHash());
        request.setVendorCode(actualVendorCode(context, result));
        request.setInterfaceCode(context.getApiCode());
        request.setDataType(context.getDataTypeCode());
        request.setTenantId(context.getTenantId());
        request.setCallerId(context.getCallerId());
        request.setVendorId(longValue(result.get("actualVendorId"), context.getVendorId()));
        request.setCallTime(callTime);
        request.setSuccess(success);
        request.setCached(cached);
        request.setResponseContractValid(Boolean.TRUE.equals(result.get("responseContractValid")));
        request.setLatencyMs(latencyMs);
        request.setHttpStatus(success ? 200 : 502);
        request.setPluginId(pluginId);
        request.setPluginVersion(pluginVersion);
        request.setPipelineVersion(pipelineVersion);
        request.setSnapshotHash(snapshotHash);
        request.setHashAlgorithm(hashAlgorithm);
        request.setIntegrityHash(integrityHash);
        try {
            request.setMeteringFacts(billingFactExtractor.extract(
                    policy, result, context.getParams()));
            request.setAdditionalPlans(buildAdditionalPlans(policy, result, context.getParams()));
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_BILLING_POLICY_INVALID", "计费策略暂不可用");
        }
        Result<BillingChargeRespDTO> chargeResult;
        try {
            chargeResult = billingFeignClient.charge(request);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_BILLING_UNAVAILABLE", "计费服务暂不可用");
        }
        BillingChargeRespDTO response = chargeResult != null ? chargeResult.getData() : null;
        if (chargeResult == null || !Integer.valueOf(200).equals(chargeResult.getCode())
                || response == null || response.getFinalAmount() == null) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_BILLING_CHARGE_UNAVAILABLE", "计费结果暂不可用");
        }
        return response.getFinalAmount();
    }

    private java.util.List<BillingAdditionalPlanDTO> buildAdditionalPlans(
            BillingMeteringPolicyDTO policy, Map<String, Object> result,
            Map<String, Object> params) {
        if (policy.getAdditionalPlans() == null) return java.util.List.of();
        return policy.getAdditionalPlans().stream().map(source -> {
            if (source == null || source.getPlanId() == null) {
                throw OpenApiQueryException.serviceUnavailable(
                        "OPENAPI_BILLING_POLICY_INVALID", "计费策略暂不可用");
            }
            BillingAdditionalPlanDTO target = new BillingAdditionalPlanDTO();
            target.setPlanId(source.getPlanId());
            target.setPlanCode(source.getPlanCode());
            target.setPlanVersion(source.getPlanVersion());
            target.setTemplateCode(source.getTemplateCode());
            target.setAccountingPurpose(source.getAccountingPurpose());
            target.setPolicyHash(source.getPolicyHash());
            target.setMeteringFacts(billingFactExtractor.extract(source, result, params));
            return target;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private ResponseContractEvaluation evaluateResponseContract(OpenApiCallContext context,
                                                                 Map<String, Object> result) {
        InterfaceContractDTO contract = context.getInterfaceContract();
        if (contract == null || contract.getResponseFields() == null || contract.getResponseFields().isEmpty()) {
            return new ResponseContractEvaluation(true, List.of());
        }
        Object rawData = result.get("data");
        if (!(rawData instanceof Map<?, ?> map)) {
            return new ResponseContractEvaluation(false, List.of("data类型必须为object"));
        }
        InterfaceContractValidator.ValidationResult validation = InterfaceContractValidator.validate(
                contract.getResponseFields(), (Map<String, Object>) map, false);
        return new ResponseContractEvaluation(validation.valid(), validation.errors());
    }

    private void applyContractViolation(Map<String, Object> result, List<String> errors) {
        ConnectorErrorPolicy policy = ConnectorErrorPolicy.CONTRACT_VIOLATION;
        result.put("success", false);
        result.put("data", Collections.emptyMap());
        result.put("errorCategory", ErrorCategory.CONTRACT_VIOLATION.name());
        result.put("connectorErrorCode", "RESPONSE_CONTRACT_INVALID");
        result.put("errorCode", policy.externalCode());
        result.put("errorMsg", "厂商响应不符合接口契约");
        result.put("billingSignal", policy.billingSignal(null).name());
        result.put("cacheSignal", policy.cacheSignal(null).name());
        result.put("deliveryState", policy.deliveryState(deliveryState(result.get("deliveryState"))).name());
        result.put("responseContractValid", false);
        result.put("responseContractErrors", List.copyOf(errors));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readResponseData(String responseData) {
        if (responseData == null || responseData.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseData, Map.class);
            return parsed == null ? null : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private OpenApiQueryRespVO buildResponse(OpenApiCallContext context, String platformRequestId,
                                             Map<String, Object> result, boolean cached,
                                             Long cacheSourceRecordId, LocalDateTime requestTime,
                                             LocalDateTime responseTime, long duration,
                                             BigDecimal cost) {
        OpenApiQueryRespVO resp = new OpenApiQueryRespVO();
        resp.setRequestId(normalize(context.getExternalRequestId()) != null ? context.getExternalRequestId() : platformRequestId);
        resp.setPlatformRequestId(platformRequestId);
        resp.setApiCode(context.getApiCode());
        resp.setApiVersion(normalize(context.getApiVersion()) != null ? context.getApiVersion() : DEFAULT_API_VERSION);
        resp.setProductCode(context.getProductCode());
        resp.setSceneCode(context.getSceneCode());
        resp.setSuccess(Boolean.TRUE.equals(result.get("success")));
        Object data = result.get("data");
        resp.setData(data instanceof Map ? (Map<String, Object>) data : Collections.emptyMap());
        resp.setErrorCode(result.get("errorCode") != null ? String.valueOf(result.get("errorCode")) : null);
        resp.setErrorMsg(result.get("errorMsg") != null ? String.valueOf(result.get("errorMsg")) : null);
        resp.setCached(cached);
        resp.setCacheSourceRecordId(cacheSourceRecordId);
        resp.setRequestTime(requestTime);
        resp.setResponseTime(responseTime);
        resp.setDurationMs(duration);
        resp.setLatency(duration);
        resp.setCost(cost);
        return resp;
    }

    private CallRecord buildRecord(OpenApiCallContext context, String platformRequestId, String requestHash,
                                   Map<String, Object> result, boolean success, long duration,
                                   BigDecimal cost, boolean cacheHit, Long cacheSourceRecordId,
                                   LocalDateTime requestTime, LocalDateTime responseTime) {
        CallRecord record = new CallRecord();
        record.setRequestId(platformRequestId);
        record.setTraceId(normalize(context.getTraceId()));
        record.setTenantId(context.getTenantId());
        record.setCallerId(context.getCallerId());
        record.setApiKeyId(context.getApiKeyId());
        record.setInterfaceId(context.getInterfaceId());
        record.setVendorId(longValue(result.get("actualVendorId"), context.getVendorId()));
        record.setVendorCode(stringValue(result.get("actualVendorCode")) != null
                ? stringValue(result.get("actualVendorCode")) : context.getVendorCode());
        record.setApiCode(context.getApiCode());
        record.setProductId(context.getProductId());
        record.setProductCode(context.getProductCode());
        record.setProductName(context.getProductName());
        record.setSceneCode(context.getSceneCode());
        record.setSceneName(context.getSceneName());
        record.setDataType(context.getDataTypeCode());
        record.setDataTypeCode(context.getDataTypeCode());
        record.setRequestHash(requestHash);
        record.setSuccess(success);
        record.setErrorCode(result.get("errorCode") != null ? String.valueOf(result.get("errorCode")) : null);
        record.setErrorMsg(result.get("errorMsg") != null ? String.valueOf(result.get("errorMsg")) : null);
        record.setLatency((int) duration);
        record.setDurationMs((int) duration);
        record.setCost(cost);
        record.setCached(cacheHit);
        record.setUseCache(Boolean.TRUE.equals(context.getUseCache())
                && "CACHEABLE".equals(String.valueOf(result.get("cacheSignal"))));
        record.setCacheDays(context.getCacheDays());
        record.setCacheHit(cacheHit);
        record.setCacheScope(normalize(context.getCacheScope()) != null ? context.getCacheScope() : "GLOBAL");
        record.setCacheSourceRecordId(cacheSourceRecordId);
        record.setPluginId(stringValue(result.get("pluginId")));
        record.setPluginVersion(stringValue(result.get("pluginVersion")));
        record.setPipelineVersion(integerValue(result.get("pipelineVersion")));
        record.setSnapshotHash(stringValue(result.get("snapshotHash")));
        record.setHashAlgorithm(stringValue(result.get("hashAlgorithm")));
        record.setIntegrityHash(stringValue(result.get("integrityHash")));
        record.setRequestTime(requestTime);
        record.setResponseAt(responseTime);
        record.setCallTime(requestTime);
        applyResponseContractResult(record, context, result);
        try {
            record.setRequestParams(objectMapper.writeValueAsString(sanitizeForRecord(context.getParams())));
            Map<String, Object> responseForRecord = sanitizeForRecord(result);
            record.setResponseData(objectMapper.writeValueAsString(responseForRecord));
        } catch (Exception e) {
            log.warn("Failed to serialize call record payload: requestId={}, type={}",
                    platformRequestId, e.getClass().getSimpleName());
            record.setRequestParams("{}");
            record.setResponseData("{}");
        }
        return record;
    }

    private void copyConnectorTrace(CallRecord source, CallRecord target) {
        target.setPluginId(source.getPluginId());
        target.setPluginVersion(source.getPluginVersion());
        target.setPipelineVersion(source.getPipelineVersion());
        target.setSnapshotHash(source.getSnapshotHash());
        target.setHashAlgorithm(source.getHashAlgorithm());
        target.setIntegrityHash(source.getIntegrityHash());
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long longValue(Object value, Long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String actualVendorCode(OpenApiCallContext context, Map<String, Object> result) {
        String actual = stringValue(result.get("actualVendorCode"));
        return actual != null ? actual : context.getVendorCode();
    }

    private RequestDeliveryState deliveryState(Object value) {
        if (value == null) return null;
        try {
            return RequestDeliveryState.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void applyResponseContractResult(CallRecord record, OpenApiCallContext context,
                                             Map<String, Object> result) {
        Object explicitValid = result.get("responseContractValid");
        if (explicitValid instanceof Boolean valid) {
            record.setResponseContractValid(valid);
            Object explicitErrors = result.get("responseContractErrors");
            if (!valid && explicitErrors != null) {
                try {
                    record.setResponseContractErrors(objectMapper.writeValueAsString(explicitErrors));
                } catch (Exception ignored) {
                    record.setResponseContractErrors("[]");
                }
                Metrics.counter("openapi.response.contract.invalid", "apiCode", context.getApiCode()).increment();
            }
            return;
        }
        if (!Boolean.TRUE.equals(result.get("success"))) return;
        ResponseContractEvaluation validation = evaluateResponseContract(context, result);
        record.setResponseContractValid(validation.valid());
        if (!validation.valid()) {
            try {
                record.setResponseContractErrors(objectMapper.writeValueAsString(validation.errors()));
            } catch (Exception ignored) {
                record.setResponseContractErrors("[]");
            }
            Metrics.counter("openapi.response.contract.invalid", "apiCode", context.getApiCode()).increment();
            log.warn("OpenAPI响应契约不匹配: requestId={}, apiCode={}, errors={}",
                    record.getRequestId(), context.getApiCode(), validation.errors());
        }
    }

    private String buildRequestHash(OpenApiCallContext context) {
        try {
            Map<String, Object> cacheIdentity = new LinkedHashMap<>();
            cacheIdentity.put(
                    "apiVersion",
                    normalize(context.getApiVersion()) != null
                            ? context.getApiVersion().trim()
                            : DEFAULT_API_VERSION);
            cacheIdentity.put(
                    "params",
                    context.getParams() != null
                            ? context.getParams()
                            : Collections.emptyMap());
            String canonicalParams = objectMapper.writeValueAsString(cacheIdentity);
            return DigestUtil.sha256Hex(canonicalParams);
        } catch (Exception e) {
            return DigestUtil.sha256Hex(
                    String.valueOf(context.getApiVersion())
                            + ":"
                            + String.valueOf(context.getParams()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeForRecord(Map<String, Object> source) {
        return sanitizeRecordMap(source, 0);
    }

    private Map<String, Object> sanitizeRecordMap(Map<String, Object> source, int depth) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        if (depth >= 16) return Map.of("_truncated", true);
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (sanitized.size() >= 500) return;
            if (isSensitiveKey(key)) {
                sanitized.put(key, MASKED_VALUE);
            } else {
                sanitized.put(key, sanitizeRecordValue(value, depth + 1));
            }
        });
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeRecordValue(Object value, int depth) {
        if (depth >= 16) return "[TRUNCATED]";
        if (value instanceof Map<?, ?> nested) {
            return sanitizeRecordMap((Map<String, Object>) nested, depth);
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.List<Object> sanitized = new java.util.ArrayList<>();
            for (Object item : iterable) {
                if (sanitized.size() >= 500) break;
                sanitized.add(sanitizeRecordValue(item, depth + 1));
            }
            return sanitized;
        }
        if (value != null && value.getClass().isArray()) {
            java.util.List<Object> sanitized = new java.util.ArrayList<>();
            int length = Math.min(java.lang.reflect.Array.getLength(value), 500);
            for (int index = 0; index < length; index++) {
                sanitized.add(sanitizeRecordValue(java.lang.reflect.Array.get(value, index), depth + 1));
            }
            return sanitized;
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String lower = key != null ? key.toLowerCase() : "";
        return lower.contains("name")
                || lower.contains("phone")
                || lower.contains("mobile")
                || lower.contains("idcard")
                || lower.contains("id_card")
                || lower.contains("cert")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("password")
                || lower.contains("passwd")
                || lower.contains("authorization")
                || lower.contains("credential")
                || lower.contains("privatekey")
                || lower.contains("private_key")
                || lower.equals("apikey")
                || lower.equals("api_key");
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record ResponseContractEvaluation(boolean valid, List<String> errors) {
    }

    public static class OpenApiCallContext {
        private String externalRequestId;
        private String traceId;
        private String apiCode;
        private String apiVersion;
        private Long callerId;
        private Long tenantId;
        private Long apiKeyId;
        private Long interfaceId;
        private Long vendorId;
        private String vendorCode;
        private String fallbackVendorCode;
        private String dataTypeCode;
        private VendorConfigDTO vendorConfig;
        private VendorConfigDTO primaryVendorConfig;
        private VendorConfigDTO fallbackVendorConfig;
        private Long productId;
        private String productCode;
        private String productName;
        private String sceneCode;
        private String sceneName;
        private Boolean useCache;
        private Integer cacheDays;
        private String cacheScope;
        private Map<String, Object> params;
        private InterfaceContractDTO interfaceContract;

        public String getExternalRequestId() { return externalRequestId; }
        public void setExternalRequestId(String externalRequestId) { this.externalRequestId = externalRequestId; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getApiCode() { return apiCode; }
        public void setApiCode(String apiCode) { this.apiCode = apiCode; }
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
        public Long getCallerId() { return callerId; }
        public void setCallerId(Long callerId) { this.callerId = callerId; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getApiKeyId() { return apiKeyId; }
        public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }
        public Long getInterfaceId() { return interfaceId; }
        public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }
        public Long getVendorId() { return vendorId; }
        public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
        public String getVendorCode() { return vendorCode; }
        public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
        public String getFallbackVendorCode() { return fallbackVendorCode; }
        public void setFallbackVendorCode(String fallbackVendorCode) { this.fallbackVendorCode = fallbackVendorCode; }
        public String getDataTypeCode() { return dataTypeCode; }
        public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
        public VendorConfigDTO getVendorConfig() { return vendorConfig; }
        public void setVendorConfig(VendorConfigDTO vendorConfig) { this.vendorConfig = vendorConfig; }
        public VendorConfigDTO getPrimaryVendorConfig() { return primaryVendorConfig; }
        public void setPrimaryVendorConfig(VendorConfigDTO primaryVendorConfig) {
            this.primaryVendorConfig = primaryVendorConfig;
        }
        public VendorConfigDTO getFallbackVendorConfig() { return fallbackVendorConfig; }
        public void setFallbackVendorConfig(VendorConfigDTO fallbackVendorConfig) {
            this.fallbackVendorConfig = fallbackVendorConfig;
        }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getSceneCode() { return sceneCode; }
        public void setSceneCode(String sceneCode) { this.sceneCode = sceneCode; }
        public String getSceneName() { return sceneName; }
        public void setSceneName(String sceneName) { this.sceneName = sceneName; }
        public Boolean getUseCache() { return useCache; }
        public void setUseCache(Boolean useCache) { this.useCache = useCache; }
        public Integer getCacheDays() { return cacheDays; }
        public void setCacheDays(Integer cacheDays) { this.cacheDays = cacheDays; }
        public String getCacheScope() { return cacheScope; }
        public void setCacheScope(String cacheScope) { this.cacheScope = cacheScope; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
        public InterfaceContractDTO getInterfaceContract() { return interfaceContract; }
        public void setInterfaceContract(InterfaceContractDTO interfaceContract) { this.interfaceContract = interfaceContract; }
    }
}
