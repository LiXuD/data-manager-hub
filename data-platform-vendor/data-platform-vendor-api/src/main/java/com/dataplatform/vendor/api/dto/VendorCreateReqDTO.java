package com.dataplatform.vendor.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 创建厂商请求DTO
 */
@Data
public class VendorCreateReqDTO implements Serializable {

    @NotBlank(message = "厂商代码不能为空")
    private String vendorCode;

    @NotBlank(message = "厂商名称不能为空")
    private String vendorName;

    private String vendorType;

    private String contactPerson;

    private String contactPhone;

    private String contactEmail;

    private String secretKey;

    private LocalDate contractStart;

    private LocalDate contractEnd;
}
