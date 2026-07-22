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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
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
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public OpenApiQueryRespVO query(OpenApiCallContext context) {
        LocalDateTime requestTime = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        String platformRequestId = generateRequestId();
        String requestHash = buildRequestHash(context.getParams());
        boolean useCache = Boolean.TRUE.equals(context.getUseCache());
        BillingMeteringPolicyDTO meteringPolicy = resolveMeteringPolicy(context, requestTime);

        if (useCache) {
            CallRecord cachedRecord = callRecordService.findLatestReusableCache(
                    context.getApiCode(),
                    requestHash,
                    context.getCallerId(),
                    requestTime.minusDays(context.getCacheDays()),
                    context.getCacheScope());
            if (cachedRecord != null) {
                LocalDateTime responseTime = LocalDateTime.now();
                long duration = System.currentTimeMillis() - startTime;
                Map<String, Object> cachedResult = readResponseData(cachedRecord.getResponseData());
                BigDecimal cost = charge(context, meteringPolicy, platformRequestId, duration,
                        requestTime, true, true, cachedResult);
                CallRecord record = buildRecord(context, platformRequestId, requestHash, cachedResult,
                        true, duration, cost, true, cachedRecord.getId(), requestTime, responseTime);
                callRecordEventPublisher.publish(record);
                return buildResponse(context, platformRequestId, cachedResult, true,
                        cachedRecord.getId(), requestTime, responseTime, duration, cost);
            }
        }

        Map<String, Object> vendorResult = vendorProxyService.callVendor(
                context.getVendorCode(),
                context.getDataTypeCode(),
                context.getParams(),
                context.getVendorConfig());

        LocalDateTime responseTime = LocalDateTime.now();
        long duration = System.currentTimeMillis() - startTime;
        boolean success = Boolean.TRUE.equals(vendorResult.get("success"));
        BigDecimal cost = charge(context, meteringPolicy, platformRequestId, duration,
                requestTime, success, false, vendorResult);
        vendorResult.put("requestId", platformRequestId);
        vendorResult.put("cached", false);
        vendorResult.put("latency", duration);

        CallRecord record = buildRecord(context, platformRequestId, requestHash, vendorResult,
                success, duration, cost, false, null, requestTime, responseTime);
        callRecordEventPublisher.publish(record);
        return buildResponse(context, platformRequestId, vendorResult, false,
                null, requestTime, responseTime, duration, cost);
    }

    private BillingMeteringPolicyDTO resolveMeteringPolicy(OpenApiCallContext context,
                                                           LocalDateTime callTime) {
        Result<BillingMeteringPolicyDTO> result = billingFeignClient.getMeteringPolicy(
                context.getVendorCode(), context.getApiCode(), callTime);
        BillingMeteringPolicyDTO policy = result != null ? result.getData() : null;
        if (policy == null || policy.getPlanId() == null) {
            throw new IllegalStateException("Billing service returned an empty metering policy");
        }
        return policy;
    }

    private BigDecimal charge(OpenApiCallContext context, BillingMeteringPolicyDTO policy,
                              String requestId, long latencyMs, LocalDateTime callTime,
                              boolean success, boolean cached, Map<String, Object> result) {
        BillingChargeReqDTO request = new BillingChargeReqDTO();
        request.setRequestId(requestId);
        request.setPlanId(policy.getPlanId());
        request.setPlanVersion(policy.getPlanVersion());
        request.setPolicyHash(policy.getPolicyHash());
        request.setVendorCode(context.getVendorCode());
        request.setInterfaceCode(context.getApiCode());
        request.setDataType(context.getDataTypeCode());
        request.setTenantId(context.getTenantId());
        request.setCallerId(context.getCallerId());
        request.setVendorId(context.getVendorId());
        request.setCallTime(callTime);
        request.setSuccess(success);
        request.setCached(cached);
        request.setResponseContractValid(isResponseContractValid(context, result));
        request.setLatencyMs(latencyMs);
        request.setHttpStatus(success ? 200 : 502);
        request.setMeteringFacts(billingFactExtractor.extract(
                policy, result, context.getParams()));
        request.setAdditionalPlans(buildAdditionalPlans(policy, result, context.getParams()));
        Result<BillingChargeRespDTO> chargeResult = billingFeignClient.charge(request);
        BillingChargeRespDTO response = chargeResult != null ? chargeResult.getData() : null;
        if (response == null || response.getFinalAmount() == null) {
            throw new IllegalStateException("Billing service returned an empty charge result");
        }
        return response.getFinalAmount();
    }

    private java.util.List<BillingAdditionalPlanDTO> buildAdditionalPlans(
            BillingMeteringPolicyDTO policy, Map<String, Object> result,
            Map<String, Object> params) {
        if (policy.getAdditionalPlans() == null) return java.util.List.of();
        return policy.getAdditionalPlans().stream().map(source -> {
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
    private boolean isResponseContractValid(OpenApiCallContext context, Map<String, Object> result) {
        InterfaceContractDTO contract = context.getInterfaceContract();
        if (contract == null || contract.getResponseFields() == null || contract.getResponseFields().isEmpty()) {
            return true;
        }
        Object rawData = result.get("data");
        return rawData instanceof Map<?, ?> map
                && InterfaceContractValidator.validate(
                    contract.getResponseFields(), (Map<String, Object>) map, false).valid();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readResponseData(String responseData) {
        if (responseData == null || responseData.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(responseData, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
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
        record.setVendorId(context.getVendorId());
        record.setVendorCode(context.getVendorCode());
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
        record.setUseCache(Boolean.TRUE.equals(context.getUseCache()));
        record.setCacheDays(context.getCacheDays());
        record.setCacheHit(cacheHit);
        record.setCacheScope(normalize(context.getCacheScope()) != null ? context.getCacheScope() : "GLOBAL");
        record.setCacheSourceRecordId(cacheSourceRecordId);
        record.setRequestTime(requestTime);
        record.setResponseAt(responseTime);
        record.setCallTime(requestTime);
        applyResponseContractResult(record, context, result);
        try {
            record.setRequestParams(objectMapper.writeValueAsString(sanitizeForRecord(context.getParams())));
            Map<String, Object> responseForRecord = Boolean.TRUE.equals(context.getUseCache())
                    ? result : sanitizeForRecord(result);
            record.setResponseData(objectMapper.writeValueAsString(responseForRecord));
        } catch (Exception e) {
            record.setRequestParams("{}");
            record.setResponseData("{}");
        }
        return record;
    }

    @SuppressWarnings("unchecked")
    private void applyResponseContractResult(CallRecord record, OpenApiCallContext context,
                                             Map<String, Object> result) {
        InterfaceContractDTO contract = context.getInterfaceContract();
        if (contract == null || contract.getResponseFields() == null || contract.getResponseFields().isEmpty()) {
            return;
        }
        Object rawData = result.get("data");
        InterfaceContractValidator.ValidationResult validation;
        if (rawData instanceof Map<?, ?> map) {
            validation = InterfaceContractValidator.validate(
                    contract.getResponseFields(), (Map<String, Object>) map, false);
        } else {
            validation = new InterfaceContractValidator.ValidationResult(
                    false, java.util.List.of("data类型必须为object"));
        }
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

    private String buildRequestHash(Map<String, Object> params) {
        try {
            String canonicalParams = objectMapper.writeValueAsString(params != null ? params : Collections.emptyMap());
            return DigestUtil.sha256Hex(canonicalParams);
        } catch (Exception e) {
            return DigestUtil.sha256Hex(String.valueOf(params));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeForRecord(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (isSensitiveKey(key)) {
                sanitized.put(key, MASKED_VALUE);
            } else if (value instanceof Map<?, ?> nested) {
                sanitized.put(key, sanitizeForRecord((Map<String, Object>) nested));
            } else {
                sanitized.put(key, value);
            }
        });
        return sanitized;
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
                || lower.contains("token");
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

    public static class OpenApiCallContext {
        private String externalRequestId;
        private String traceId;
        private String apiCode;
        private String apiVersion;
        private Long callerId;
        private Long tenantId;
        private Long apiKeyId;
        private Long vendorId;
        private String vendorCode;
        private String dataTypeCode;
        private VendorConfigDTO vendorConfig;
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
        public Long getVendorId() { return vendorId; }
        public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
        public String getVendorCode() { return vendorCode; }
        public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
        public String getDataTypeCode() { return dataTypeCode; }
        public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
        public VendorConfigDTO getVendorConfig() { return vendorConfig; }
        public void setVendorConfig(VendorConfigDTO vendorConfig) { this.vendorConfig = vendorConfig; }
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
