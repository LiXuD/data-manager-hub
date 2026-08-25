package com.dataplatform.access.connector.service;

import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import java.util.Map;

/** Executes one fixed, published connector pipeline for a vendor configuration. */
public interface ConnectorVendorExecutor {

    ConnectorExecutionResult execute(
            VendorConfigDTO config,
            String vendorCode,
            String dataTypeCode,
            Map<String, Object> parameters);

    /** Carries the platform request ID that is also used by billing and the call record. */
    default ConnectorExecutionResult execute(
            VendorConfigDTO config,
            String vendorCode,
            String dataTypeCode,
            Map<String, Object> parameters,
            String platformRequestId) {
        return execute(config, vendorCode, dataTypeCode, parameters);
    }
}
