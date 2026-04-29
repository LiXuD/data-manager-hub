package com.dataplatform.vendor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建厂商配置请求DTO
 */
@Data
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
}
