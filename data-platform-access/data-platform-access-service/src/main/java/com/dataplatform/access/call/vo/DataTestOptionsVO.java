package com.dataplatform.access.call.vo;

import java.util.List;

/**
 * Minimal, read-only option set for the authenticated data-test capability.
 * It intentionally contains no management-only fields or secret material.
 */
public record DataTestOptionsVO(
        List<VendorOption> vendors,
        List<DataTypeOption> dataTypes,
        List<InterfaceOption> interfaces,
        List<SceneOption> scenes,
        List<ProductOption> products) {

    public record VendorOption(Long id, String vendorName) {
    }

    public record DataTypeOption(Long id, String dataTypeCode, String dataTypeName) {
    }

    public record InterfaceOption(
            Long id,
            String interfaceCode,
            String interfaceName,
            Long vendorId,
            Long dataTypeId) {
    }

    public record SceneOption(Long id, String sceneCode, String sceneName, String status) {
    }

    public record ProductOption(Long id, String productCode, String productName, String status) {
    }
}
