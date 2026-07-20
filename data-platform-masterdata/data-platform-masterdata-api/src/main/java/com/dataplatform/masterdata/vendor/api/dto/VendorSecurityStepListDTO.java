package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VendorSecurityStepListDTO implements Serializable {

    private Integer version;
    private List<VendorSecurityStepDTO> steps = new ArrayList<>();

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public List<VendorSecurityStepDTO> getSteps() { return steps; }
    public void setSteps(List<VendorSecurityStepDTO> steps) {
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
    }
}
