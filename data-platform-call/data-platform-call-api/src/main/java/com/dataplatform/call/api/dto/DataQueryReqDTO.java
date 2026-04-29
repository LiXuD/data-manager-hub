package com.dataplatform.call.api.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

@Data
public class DataQueryReqDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String dataType;
    private String dataTypeCode;
    private Map<String, Object> params;
    private Boolean useCache;
}
