package com.dataplatform.common.plugin.artifact;

import java.util.Set;

/** Immutable vendor and data-type compatibility declared by a Manifest v2 plugin. */
public record PluginCompatibility(Set<String> vendorCodes, Set<String> dataTypeCodes) {

    private static final PluginCompatibility EMPTY = new PluginCompatibility(Set.of(), Set.of());

    public PluginCompatibility {
        vendorCodes = vendorCodes == null ? Set.of() : Set.copyOf(vendorCodes);
        dataTypeCodes = dataTypeCodes == null ? Set.of() : Set.copyOf(dataTypeCodes);
    }

    public static PluginCompatibility empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return vendorCodes.isEmpty() && dataTypeCodes.isEmpty();
    }

    public boolean supportsVendor(String vendorCode) {
        return vendorCodes.isEmpty() || vendorCodes.contains("*") || vendorCodes.contains(vendorCode);
    }

    public boolean supportsDataType(String dataTypeCode) {
        return dataTypeCodes.isEmpty() || dataTypeCodes.contains("*") || dataTypeCodes.contains(dataTypeCode);
    }
}
