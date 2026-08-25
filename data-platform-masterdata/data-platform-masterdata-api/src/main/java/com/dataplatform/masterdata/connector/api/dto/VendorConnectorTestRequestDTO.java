package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.Map;

public record VendorConnectorTestRequestDTO(Map<String, Object> params) implements Serializable {
}
