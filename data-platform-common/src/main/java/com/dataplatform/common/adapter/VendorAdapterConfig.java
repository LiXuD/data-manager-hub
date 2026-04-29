package com.dataplatform.common.adapter;

import java.util.Map;

/**
 * 厂商适配器配置
 */
public class VendorAdapterConfig {

    private String vendorCode;
    private String dataTypeCode;
    private String apiUrl;
    private String method;
    private Integer timeout;
    private Integer retryCount;
    private Map<String, String> headers;
    private String requestTemplate;
    private String responseMapping;
    private String signType;
    private String secretKey;

    // Getters and Setters
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getDataTypeCode() { return dataTypeCode; }
    public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String requestTemplate) { this.requestTemplate = requestTemplate; }
    public String getResponseMapping() { return responseMapping; }
    public void setResponseMapping(String responseMapping) { this.responseMapping = responseMapping; }
    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
}
