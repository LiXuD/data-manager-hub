package com.dataplatform.caller.api.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ApiKeyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long callerId;
    private String apiKey;
    private String apiSecret;
    private String description;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
