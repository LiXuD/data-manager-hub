package com.dataplatform.masterdata.vendor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 创建厂商配置请求DTO
 */
public class VendorConfigCreateReqDTO implements Serializable {

    @NotNull(message = "厂商ID不能为空")
    private Long vendorId;

    @NotBlank(message = "数据类型不能为空")
    private String dataTypeCode;

    private Long interfaceId;

    @NotBlank(message = "API地址不能为空")
    private String apiUrl;

    private String method;

    private Integer timeout;

    private Integer retryCount;

    private Integer circuitThreshold;

    private Integer circuitTimeout;

    private String signType;

    private String encryptType;

    private String headerConfig;

    private String requestTemplate;

    private String responseMapping;

    private Long fallbackVendorId;

    private String status;

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getDataTypeCode() {
        return dataTypeCode;
    }

    public void setDataTypeCode(String dataTypeCode) {
        this.dataTypeCode = dataTypeCode;
    }

    public Long getInterfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(Long interfaceId) {
        this.interfaceId = interfaceId;
    }

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

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getEncryptType() {
        return encryptType;
    }

    public void setEncryptType(String encryptType) {
        this.encryptType = encryptType;
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
