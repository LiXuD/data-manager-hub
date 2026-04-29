package com.dataplatform.vendor.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新厂商配置请求DTO
 */
@Data
public class VendorConfigUpdateReqDTO implements Serializable {

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
}
