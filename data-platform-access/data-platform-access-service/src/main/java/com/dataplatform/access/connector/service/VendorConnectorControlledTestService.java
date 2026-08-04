package com.dataplatform.access.connector.service;

import com.dataplatform.access.connector.api.dto.ConnectorStageTimingDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.TransportStatus;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Isolated management test path with no billing/cache/call-record collaborators. */
@Service
public class VendorConnectorControlledTestService {

    private final VendorConfigInternalFeignClient configClient;
    private final VendorInternalFeignClient vendorClient;
    private final DefaultConnectorVendorExecutor executor;
    private final ConnectorTestResultSanitizer sanitizer;

    public VendorConnectorControlledTestService(
            VendorConfigInternalFeignClient configClient,
            VendorInternalFeignClient vendorClient,
            DefaultConnectorVendorExecutor executor,
            ConnectorTestResultSanitizer sanitizer) {
        this.configClient = configClient;
        this.vendorClient = vendorClient;
        this.executor = executor;
        this.sanitizer = sanitizer;
    }

    public VendorConnectorTestRespDTO test(VendorConnectorTestReqDTO request) {
        if (request == null || request.getVendorConfigId() == null
                || request.getPipelineSnapshot() == null || request.getPipelineSnapshot().isEmpty()) {
            throw new IllegalArgumentException("vendorConfigId and pipelineSnapshot are required");
        }
        VendorConfigDTO config = data(configClient.getById(request.getVendorConfigId()),
                "Vendor configuration is unavailable");
        if (config.getVendorId() == null) {
            throw new IllegalArgumentException("Vendor configuration has no vendor");
        }
        VendorInfoDTO vendor = data(vendorClient.getById(config.getVendorId()), "Vendor is unavailable");
        ConnectorExecutionResult result = executor.executeDraft(config, vendor.getVendorCode(),
                request.getPipelineSnapshot(), request.getParams());
        VendorConnectorTestRespDTO response = new VendorConnectorTestRespDTO();
        response.setSuccess(success(result));
        response.setErrorCategory(result.errorCategory() != null ? result.errorCategory().name() : null);
        response.setErrorCode(result.errorCode());
        response.setSafeMessage(safeTestMessage(result));
        response.setNormalizedData(sanitizer.sanitize(result.normalizedData()));
        response.setStageTimings(result.stageTimings().stream().map(timing -> {
            ConnectorStageTimingDTO dto = new ConnectorStageTimingDTO();
            dto.setStageKey(timing.stageKey());
            dto.setCapability(timing.capability().name());
            dto.setPluginId(timing.pluginId());
            dto.setPluginVersion(timing.pluginVersion());
            dto.setDurationMs(Math.max(0L, timing.duration().toMillis()));
            return dto;
        }).toList());
        return response;
    }

    private boolean success(ConnectorExecutionResult result) {
        return result.errorCategory() == null && result.transportStatus() == TransportStatus.SUCCESS
                && result.businessStatus() == BusinessStatus.SUCCESS;
    }

    private <T> T data(Result<T> response, String message) {
        if (response == null || response.getData() == null) throw new IllegalArgumentException(message);
        return response.getData();
    }

    private String safeTestMessage(ConnectorExecutionResult result) {
        if (result.errorCategory() == null) return "Connector test completed";
        return "Connector test failed: " + result.errorCategory().name();
    }
}
