package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConnectorSecretResolutionDTO implements Serializable {
    private Map<String, String> resolvedSecrets = new LinkedHashMap<>();

    public Map<String, String> getResolvedSecrets() { return resolvedSecrets; }
    public void setResolvedSecrets(Map<String, String> resolvedSecrets) {
        this.resolvedSecrets = resolvedSecrets == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(resolvedSecrets);
    }
}
