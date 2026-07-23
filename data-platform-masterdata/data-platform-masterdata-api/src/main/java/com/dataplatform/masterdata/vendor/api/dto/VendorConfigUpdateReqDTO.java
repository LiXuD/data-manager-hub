package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;

/**
 * 更新厂商配置请求DTO
 */
public class VendorConfigUpdateReqDTO implements Serializable {

    private String apiUrl;

    private String method;

    private Integer timeout;

    private Integer retryCount;

    private Integer circuitThreshold;

    private Integer circuitTimeout;

    private String headerConfig;

    private String requestTemplate;

    private String responseMapping;

    private Long fallbackVendorId;

    private String status;

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getCircuitThreshold() {
        return circuitThreshold;
    }

    public void setCircuitThreshold(Integer circuitThreshold) {
        this.circuitThreshold = circuitThreshold;
    }

    public Integer getCircuitTimeout() {
        return circuitTimeout;
    }

    public void setCircuitTimeout(Integer circuitTimeout) {
        this.circuitTimeout = circuitTimeout;
    }

    public String getHeaderConfig() {
        return headerConfig;
    }

    public void setHeaderConfig(String headerConfig) {
        this.headerConfig = headerConfig;
    }

    public String getRequestTemplate() {
        return requestTemplate;
    }

    public void setRequestTemplate(String requestTemplate) {
        this.requestTemplate = requestTemplate;
    }

    public String getResponseMapping() {
        return responseMapping;
    }

    public void setResponseMapping(String responseMapping) {
        this.responseMapping = responseMapping;
    }

    public Long getFallbackVendorId() {
        return fallbackVendorId;
    }

    public void setFallbackVendorId(Long fallbackVendorId) {
        this.fallbackVendorId = fallbackVendorId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
