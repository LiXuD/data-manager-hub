package com.dataplatform.access.connector.controller;

import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginStageReqDTO;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.access.connector.service.ConnectorPluginActivationService;
import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.security.InternalScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/access/connector-plugins")
public class ConnectorPluginActivationInternalController
        implements ConnectorPluginActivationInternalFeignClient {

    private final ConnectorPluginActivationService service;

    public ConnectorPluginActivationInternalController(ConnectorPluginActivationService service) {
        this.service = service;
    }

    @Override
    @InternalScope("access:connector-runtime:manage")
    @OperationLog(module = "连接器插件运行时", operation = "预加载插件版本", saveResult = false)
    public Result<ConnectorPluginActivationSummaryDTO> stage(ConnectorPluginStageReqDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        return Result.success(service.requestStage(request.getPluginId(), request.getPluginVersion()));
    }

    @Override
    @InternalScope("access:connector-runtime:read")
    public Result<ConnectorPluginActivationSummaryDTO> activation(String pluginId, String version) {
        return Result.success(service.summary(pluginId, version));
    }

    @Override
    @InternalScope("access:connector-runtime:manage")
    @OperationLog(module = "连接器插件运行时", operation = "释放插件版本", saveResult = false)
    public Result<ConnectorPluginActivationSummaryDTO> release(String pluginId, String version) {
        return Result.success(service.requestRelease(pluginId, version));
    }
}
