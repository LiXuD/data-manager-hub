package com.dataplatform.caller.api.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CallerInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String callerName;
    private String callerCode;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
