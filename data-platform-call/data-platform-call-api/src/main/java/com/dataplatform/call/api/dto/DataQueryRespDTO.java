package com.dataplatform.call.api.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class DataQueryRespDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Object data;
    private Boolean success;
    private String errorCode;
    private String errorMsg;
    private Integer latency;
    private Boolean cached;
}
