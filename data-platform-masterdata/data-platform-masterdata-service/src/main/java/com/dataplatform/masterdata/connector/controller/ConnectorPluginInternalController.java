package com.dataplatform.masterdata.connector.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.feign.ConnectorPluginInternalFeignClient;
import com.dataplatform.masterdata.connector.service.ConnectorPluginCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/masterdata/connector-plugins")
@InternalScope("masterdata:connector-artifact:read")
public class ConnectorPluginInternalController implements ConnectorPluginInternalFeignClient {
    private final ConnectorPluginCatalogService service;

    public ConnectorPluginInternalController(ConnectorPluginCatalogService service) {
        this.service = service;
    }

    @Override
    @GetMapping("/{pluginId}/versions/{version}/artifact")
    public Result<PluginArtifactDescriptorDTO> getArtifact(@PathVariable String pluginId,
                                                           @PathVariable String version) {
        return Result.success(service.artifact(pluginId, version));
    }

    @Override
    @GetMapping("/runtime/required-artifacts")
    public Result<List<PluginArtifactDescriptorDTO>> getRequiredArtifacts() {
        return Result.success(service.requiredArtifacts());
    }
}
