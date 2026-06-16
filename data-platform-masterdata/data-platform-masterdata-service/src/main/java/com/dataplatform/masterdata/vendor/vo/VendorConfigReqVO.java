package com.dataplatform.masterdata.vendor.vo;

// import io.swagger.annotations.ApiModel;
// import io.swagger.annotations.ApiModelProperty;

// import javax.validation.constraints.NotBlank;
// import javax.validation.constraints.NotNull;

/**
 * 厂商配置请求VO
 */

public class VendorConfigReqVO {
    
    private Long vendorId;
    
    private String dataType;
    
    private String apiUrl;
    
    private String method = "POST";
    
    private Integer timeout = 30000;
    
    private Integer retryCount = 3;
    
    private Integer circuitThreshold = 5;
    
    private Integer circuitTimeout = 60000;
    
    private String signType;
    
    private String encryptType;
    
    private String headerConfig;
    
    private String requestTemplate;
    
    private String responseMapping;
    
    private Long fallbackVendorId;
    
    private Integer status = 1;

    // Getters and Setters
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }
    public String getEncryptType() { return encryptType; }
    public void setEncryptType(String encryptType) { this.encryptType = encryptType; }
    public String getHeaderConfig() { return headerConfig; }
    public void setHeaderConfig(String headerConfig) { this.headerConfig = headerConfig; }
    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String requestTemplate) { this.requestTemplate = requestTemplate; }
    public String getResponseMapping() { return responseMapping; }
    public void setResponseMapping(String responseMapping) { this.responseMapping = responseMapping; }
    public Long getFallbackVendorId() { return fallbackVendorId; }
    public void setFallbackVendorId(Long fallbackVendorId) { this.fallbackVendorId = fallbackVendorId; }

}