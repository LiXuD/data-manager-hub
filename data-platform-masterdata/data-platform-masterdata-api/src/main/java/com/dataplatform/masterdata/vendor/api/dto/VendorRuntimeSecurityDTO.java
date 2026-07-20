package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VendorRuntimeSecurityDTO implements Serializable {

    private List<VendorSecurityStepDTO> steps = new ArrayList<>();
    private Map<String, String> resolvedSecrets = new LinkedHashMap<>();

    public List<VendorSecurityStepDTO> getSteps() { return steps; }
    public void setSteps(List<VendorSecurityStepDTO> steps) { this.steps = steps; }
    public Map<String, String> getResolvedSecrets() { return resolvedSecrets; }
    public void setResolvedSecrets(Map<String, String> resolvedSecrets) { this.resolvedSecrets = resolvedSecrets; }
}
