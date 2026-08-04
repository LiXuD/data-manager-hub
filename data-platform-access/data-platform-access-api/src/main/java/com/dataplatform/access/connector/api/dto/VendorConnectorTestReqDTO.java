package com.dataplatform.access.connector.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Controlled connector test request; raw secrets and transport responses are never returned. */
public class VendorConnectorTestReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long vendorConfigId;
    private List<ConnectorTestPipelineStepDTO> pipelineSnapshot = new ArrayList<>();
    private Map<String, Object> params = new LinkedHashMap<>();

    public Long getVendorConfigId() { return vendorConfigId; }
    public void setVendorConfigId(Long vendorConfigId) { this.vendorConfigId = vendorConfigId; }
    public List<ConnectorTestPipelineStepDTO> getPipelineSnapshot() { return pipelineSnapshot; }
    public void setPipelineSnapshot(List<ConnectorTestPipelineStepDTO> pipelineSnapshot) {
        this.pipelineSnapshot = pipelineSnapshot != null ? pipelineSnapshot : new ArrayList<>();
    }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) {
        this.params = params != null ? params : new LinkedHashMap<>();
    }
}
