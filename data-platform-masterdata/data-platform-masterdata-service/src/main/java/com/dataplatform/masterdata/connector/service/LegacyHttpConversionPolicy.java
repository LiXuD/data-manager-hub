package com.dataplatform.masterdata.connector.service;

/** Platform-owned execution policy facts needed to prove that legacy step policy can be dropped. */
public record LegacyHttpConversionPolicy(int timeoutMs) {
    public LegacyHttpConversionPolicy {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
    }
}
