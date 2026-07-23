package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VendorSecurityCapabilityDTO implements Serializable {

    private String stepType;
    private String name;
    private List<String> directions = new ArrayList<>();
    private List<String> algorithms = new ArrayList<>();
    private Map<String, Object> defaults = new LinkedHashMap<>();

    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getDirections() { return directions; }
    public void setDirections(List<String> directions) { this.directions = directions; }
    public List<String> getAlgorithms() { return algorithms; }
    public void setAlgorithms(List<String> algorithms) { this.algorithms = algorithms; }
    public Map<String, Object> getDefaults() { return defaults; }
    public void setDefaults(Map<String, Object> defaults) { this.defaults = defaults; }
}
