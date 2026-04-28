package com.dataplatform.call.api.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CallRecordDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String requestId;
    private Long tenantId;
    private Long callerId;
    private Long vendorId;
    private String vendorCode;
    private String dataType;
    private String dataTypeCode;
    private Boolean success;
    private String errorCode;
    private String errorMsg;
    private Integer latency;
    private BigDecimal cost;
    private LocalDateTime callTime;
}
