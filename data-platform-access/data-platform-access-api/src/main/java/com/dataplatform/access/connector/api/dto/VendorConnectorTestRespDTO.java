package com.dataplatform.access.connector.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Redacted connector test outcome safe for the Masterdata management surface. */
public class VendorConnectorTestRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean success;
    private String errorCategory;
    private String errorCode;
    private String safeMessage;
    private Map<String, Object> normalizedData = new LinkedHashMap<>();
    private List<ConnectorStageTimingDTO> stageTimings = new ArrayList<>();

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getSafeMessage() { return safeMessage; }
    public void setSafeMessage(String safeMessage) { this.safeMessage = safeMessage; }
    public Map<String, Object> getNormalizedData() { return normalizedData; }
    public void setNormalizedData(Map<String, Object> normalizedData) {
        this.normalizedData = normalizedData != null ? normalizedData : new LinkedHashMap<>();
    }
    public List<ConnectorStageTimingDTO> getStageTimings() { return stageTimings; }
    public void setStageTimings(List<ConnectorStageTimingDTO> stageTimings) {
        this.stageTimings = stageTimings != null ? stageTimings : new ArrayList<>();
    }
}
