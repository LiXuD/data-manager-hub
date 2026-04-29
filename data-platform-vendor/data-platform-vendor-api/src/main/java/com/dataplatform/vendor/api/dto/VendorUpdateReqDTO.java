package com.dataplatform.vendor.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 更新厂商请求DTO
 */
@Data
public class VendorUpdateReqDTO implements Serializable {

    private String vendorName;

    private String vendorType;

    private String contactPerson;

    private String contactPhone;

    private String contactEmail;

    private String secretKey;

    private LocalDate contractStart;

    private LocalDate contractEnd;
}
