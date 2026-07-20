package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class VendorSecurityPreviewReqDTO implements Serializable {

    private String direction = "REQUEST";
    private Map<String, Object> params = new LinkedHashMap<>();
    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, String> query = new LinkedHashMap<>();
    private String body;
    private List<VendorSecurityStepDTO> steps;

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public Map<String, String> getQuery() { return query; }
    public void setQuery(Map<String, String> query) { this.query = query; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public List<VendorSecurityStepDTO> getSteps() { return steps; }
    public void setSteps(List<VendorSecurityStepDTO> steps) { this.steps = steps; }
}
