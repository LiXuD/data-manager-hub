package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class VendorSecurityPreviewDTO implements Serializable {

    private Map<String, Object> params = new LinkedHashMap<>();
    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, String> query = new LinkedHashMap<>();
    private String body;
    private Map<String, Object> stepResults = new LinkedHashMap<>();

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public Map<String, String> getQuery() { return query; }
    public void setQuery(Map<String, String> query) { this.query = query; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Map<String, Object> getStepResults() { return stepResults; }
    public void setStepResults(Map<String, Object> stepResults) { this.stepResults = stepResults; }
}
