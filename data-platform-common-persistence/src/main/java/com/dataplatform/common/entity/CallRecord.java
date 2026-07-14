package com.dataplatform.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.common.handler.JsonbTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 公共持久化层的 Call Record。
 * <p>数据库实体对象，映射业务表字段并承载持久化层数据结构。</p>
 */
@TableName(value = "call_record", autoResultMap = true)
public class CallRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;
    private String traceId;
    private Long tenantId;
    private Long callerId;
    private Long apiKeyId;
    private Long vendorId;
    private String vendorCode;
    private String apiCode;
    private Long productId;
    private String productCode;
    private String productName;
    private String sceneCode;
    private String sceneName;
    private String dataType;
    private String dataTypeCode;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String requestParams;
    private String requestHash;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String responseData;
    private Boolean success;
    private String errorCode;
    private String errorMsg;
    private Integer latency;
    private Integer durationMs;
    private BigDecimal cost;
    private Boolean cached;
    private Boolean useCache;
    private Integer cacheDays;
    private Boolean cacheHit;
    private String cacheScope;
    private Long cacheSourceRecordId;
    private LocalDateTime requestTime;
    private LocalDateTime responseAt;
    private LocalDateTime callTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getApiCode() { return apiCode; }
    public void setApiCode(String apiCode) { this.apiCode = apiCode; }
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
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getDataTypeCode() { return dataTypeCode; }
    public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getResponseData() { return responseData; }
    public void setResponseData(String responseData) { this.responseData = responseData; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Integer getLatency() { return latency; }
    public void setLatency(Integer latency) { this.latency = latency; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public Boolean getCached() { return cached; }
    public void setCached(Boolean cached) { this.cached = cached; }
    public Boolean getUseCache() { return useCache; }
    public void setUseCache(Boolean useCache) { this.useCache = useCache; }
    public Integer getCacheDays() { return cacheDays; }
    public void setCacheDays(Integer cacheDays) { this.cacheDays = cacheDays; }
    public Boolean getCacheHit() { return cacheHit; }
    public void setCacheHit(Boolean cacheHit) { this.cacheHit = cacheHit; }
    public String getCacheScope() { return cacheScope; }
    public void setCacheScope(String cacheScope) { this.cacheScope = cacheScope; }
    public Long getCacheSourceRecordId() { return cacheSourceRecordId; }
    public void setCacheSourceRecordId(Long cacheSourceRecordId) { this.cacheSourceRecordId = cacheSourceRecordId; }
    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }
    public LocalDateTime getResponseAt() { return responseAt; }
    public void setResponseAt(LocalDateTime responseAt) { this.responseAt = responseAt; }
    public LocalDateTime getCallTime() { return callTime; }
    public void setCallTime(LocalDateTime callTime) { this.callTime = callTime; }
}
