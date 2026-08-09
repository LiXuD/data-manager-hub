package com.dataplatform.masterdata.connector.service;

import com.dataplatform.access.connector.api.dto.ConnectorTestPipelineStepDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.access.connector.api.feign.VendorConnectorRuntimeInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRuntimeSnapshotDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Masterdata-side bridge for management calls that must execute the same immutable connector
 * snapshot as production OpenAPI traffic. Plugin code remains owned and executed by Access.
 */
@Service
public class ActiveVendorConnectorRuntimeService {

    private final VendorConnectorService connectorService;
    private final VendorConnectorRuntimeInternalFeignClient runtimeClient;

    public ActiveVendorConnectorRuntimeService(
            VendorConnectorService connectorService,
            VendorConnectorRuntimeInternalFeignClient runtimeClient) {
        this.connectorService = connectorService;
        this.runtimeClient = runtimeClient;
    }

    public Map<String, Object> execute(Long vendorConfigId, Map<String, Object> params) {
        VendorConnectorRuntimeSnapshotDTO snapshot = connectorService.runtimeSnapshot(vendorConfigId);
        if (snapshot == null || !"ACTIVE".equals(snapshot.status())) {
            return error("CONFIGURATION_ERROR", "CONNECTOR_NOT_PUBLISHED", "厂商配置尚未发布活动连接器版本");
        }

        VendorConnectorTestReqDTO request = new VendorConnectorTestReqDTO();
        request.setVendorConfigId(vendorConfigId);
        request.setParams(params == null ? Map.of() : params);
        request.setPipelineSnapshot(snapshot.pipelineSnapshot().stream()
                .map(this::toRuntimeStep)
                .toList());

        Result<VendorConnectorTestRespDTO> result = runtimeClient.test(request);
        if (result == null || !Integer.valueOf(200).equals(result.getCode()) || result.getData() == null) {
            return error("PLUGIN_NOT_READY", "CONNECTOR_RUNTIME_UNAVAILABLE", "连接器运行时不可用");
        }
        return toResult(result.getData(), snapshot);
    }

    private ConnectorTestPipelineStepDTO toRuntimeStep(ConnectorPipelineStepDTO step) {
        ConnectorTestPipelineStepDTO target = new ConnectorTestPipelineStepDTO();
        target.setStageKey(step.stageKey());
        target.setCapability(step.capability());
        target.setPluginId(step.pluginId());
        target.setPluginVersion(step.pluginVersion());
        target.setOrder(step.order());
        target.setEnabled(step.enabled());
        target.setConfig(step.config());
        target.setConfigHash(step.configHash());
        return target;
    }

    private Map<String, Object> toResult(
            VendorConnectorTestRespDTO response,
            VendorConnectorRuntimeSnapshotDTO snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", Boolean.TRUE.equals(response.getSuccess()));
        result.put("data", new LinkedHashMap<>(response.getNormalizedData()));
        result.put("errorCategory", response.getErrorCategory());
        result.put("errorCode", response.getErrorCode());
        result.put("errorMsg", response.getSafeMessage());
        result.put("stageTimings", List.copyOf(response.getStageTimings()));
        result.put("pipelineVersion", snapshot.versionNo());
        result.put("snapshotHash", snapshot.snapshotHash());
        result.put("runtimeMode", "PLUGIN");
        return result;
    }

    private Map<String, Object> error(String errorCategory, String errorCode, String errorMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("errorCategory", errorCategory);
        result.put("errorCode", errorCode);
        result.put("errorMsg", errorMessage);
        result.put("runtimeMode", "PLUGIN");
        return result;
    }
}
