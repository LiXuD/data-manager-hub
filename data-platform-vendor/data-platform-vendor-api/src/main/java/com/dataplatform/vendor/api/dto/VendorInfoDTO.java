package com.dataplatform.vendor.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 厂商信息DTO
 */
@Data
public class VendorInfoDTO implements Serializable {

    private Long id;

    private String vendorCode;

    private String vendorName;

    private String vendorType;

    private String status;

    private String contactPerson;

    private String contactPhone;

    private String contactEmail;

    private LocalDate contractStart;

    private LocalDate contractEnd;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
