package com.dataplatform.vendor.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 厂商配置DTO
 */
@Data
public class VendorConfigDTO implements Serializable {

    private Long id;

    private Long vendorId;

    private String vendorName;

    private Long dataTypeId;

    private String dataTypeCode;

    private Long interfaceId;

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

    private String paramMapping;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
