package com.dataplatform.masterdata.connector.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "data-platform-masterdata", contextId = "masterdataConnectorPluginInternalClient",
        path = "/internal/v1/masterdata/connector-plugins")
@InternalFeignContract
public interface ConnectorPluginInternalFeignClient {

    @GetMapping("/{pluginId}/versions/{version}/artifact")
    Result<PluginArtifactDescriptorDTO> getArtifact(
            @PathVariable("pluginId") String pluginId,
            @PathVariable("version") String version);

    @GetMapping("/runtime/required-artifacts")
    Result<List<PluginArtifactDescriptorDTO>> getRequiredArtifacts();
}
