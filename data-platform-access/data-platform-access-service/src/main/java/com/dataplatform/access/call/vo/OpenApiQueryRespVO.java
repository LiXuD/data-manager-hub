package com.dataplatform.access.call.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public class OpenApiQueryRespVO {

    private String requestId;
    private String platformRequestId;
    private String apiCode;
    private String apiVersion;
    private String productCode;
    private String sceneCode;
    private Boolean success;
    private Map<String, Object> data;
    private String errorCode;
    private String errorMsg;
    private Boolean cached;
    private Long cacheSourceRecordId;
    private LocalDateTime requestTime;
    private LocalDateTime responseTime;
    private Long durationMs;
    private BigDecimal cost;
    private Long latency;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getPlatformRequestId() {
        return platformRequestId;
    }

    public void setPlatformRequestId(String platformRequestId) {
        this.platformRequestId = platformRequestId;
    }

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Boolean getCached() {
        return cached;
    }

    public void setCached(Boolean cached) {
        this.cached = cached;
    }

    public Long getCacheSourceRecordId() {
        return cacheSourceRecordId;
    }

    public void setCacheSourceRecordId(Long cacheSourceRecordId) {
        this.cacheSourceRecordId = cacheSourceRecordId;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    public LocalDateTime getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(LocalDateTime responseTime) {
        this.responseTime = responseTime;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public Long getLatency() {
        return latency;
    }

    public void setLatency(Long latency) {
        this.latency = latency;
    }
}
