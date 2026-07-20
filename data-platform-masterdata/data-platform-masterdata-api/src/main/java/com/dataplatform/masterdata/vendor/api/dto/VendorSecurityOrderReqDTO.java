package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VendorSecurityOrderReqDTO implements Serializable {

    private Integer version;
    private String direction;
    private List<Long> orderedStepIds = new ArrayList<>();

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public List<Long> getOrderedStepIds() { return orderedStepIds; }
    public void setOrderedStepIds(List<Long> orderedStepIds) {
        this.orderedStepIds = orderedStepIds == null ? new ArrayList<>() : new ArrayList<>(orderedStepIds);
    }
}
