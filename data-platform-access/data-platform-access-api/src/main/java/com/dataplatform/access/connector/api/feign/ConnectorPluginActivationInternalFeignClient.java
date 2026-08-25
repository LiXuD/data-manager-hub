package com.dataplatform.access.connector.api.feign;

import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginStageReqDTO;
import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Internal control contract for preloading and releasing Access connector runtimes. */
@FeignClient(name = "data-platform-access", contextId = "accessConnectorPluginActivationInternalClient",
        path = "/internal/v1/access/connector-plugins")
@InternalFeignContract
public interface ConnectorPluginActivationInternalFeignClient {

    @PostMapping("/stage")
    Result<ConnectorPluginActivationSummaryDTO> stage(@RequestBody ConnectorPluginStageReqDTO request);

    @GetMapping("/{pluginId}/versions/{version}/activation")
    Result<ConnectorPluginActivationSummaryDTO> activation(
            @PathVariable("pluginId") String pluginId,
            @PathVariable("version") String version);

    @PostMapping("/{pluginId}/versions/{version}/release")
    Result<ConnectorPluginActivationSummaryDTO> release(
            @PathVariable("pluginId") String pluginId,
            @PathVariable("version") String version);
}
