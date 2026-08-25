package com.dataplatform.masterdata.connector.controller;

import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPluginDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPluginVersionDTO;
import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.ConnectorPluginCatalogService;
import com.dataplatform.masterdata.connector.service.PluginArtifactValidationException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/connector-plugin")
public class ConnectorPluginController {
    private final ConnectorPluginCatalogService service;

    public ConnectorPluginController(ConnectorPluginCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<ConnectorPluginDTO>> list() {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.list());
    }

    @GetMapping("/{pluginId}")
    public Result<ConnectorPluginDTO> get(@PathVariable String pluginId) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.get(pluginId));
    }

    @GetMapping("/{pluginId}/versions")
    public Result<List<ConnectorPluginVersionDTO>> versions(@PathVariable String pluginId) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.versions(pluginId));
    }

    @PostMapping("/versions/import")
    @OperationLog(module = "连接器插件", operation = "导入签名插件版本")
    public Result<ConnectorPluginVersionDTO> importVersion(@Valid @RequestBody PluginImportRequestDTO request) {
        if (!allowed("connector-plugin:import")) return forbidden();
        return Result.success(service.importVersion(request, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{pluginId}/versions/{version}/verify")
    @OperationLog(module = "连接器插件", operation = "重新验证插件版本")
    public Result<ConnectorPluginVersionDTO> verify(@PathVariable String pluginId,
                                                     @PathVariable String version) {
        if (!allowed("connector-plugin:verify")) return forbidden();
        return Result.success(service.verify(pluginId, version, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{pluginId}/versions/{version}/stage")
    @OperationLog(module = "连接器插件", operation = "预加载插件版本")
    public Result<ConnectorPluginActivationSummaryDTO> stage(@PathVariable String pluginId,
                                                              @PathVariable String version) {
        if (!allowed("connector-plugin:activate")) return forbidden();
        return Result.success(service.stage(pluginId, version));
    }

    @GetMapping("/{pluginId}/versions/{version}/activation")
    public Result<ConnectorPluginActivationSummaryDTO> activation(@PathVariable String pluginId,
                                                                   @PathVariable String version) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.activation(pluginId, version));
    }

    @PostMapping("/{pluginId}/versions/{version}/activate")
    @OperationLog(module = "连接器插件", operation = "激活插件版本")
    public Result<ConnectorPluginVersionDTO> activate(@PathVariable String pluginId,
                                                       @PathVariable String version) {
        if (!allowed("connector-plugin:activate")) return forbidden();
        return Result.success(service.activate(pluginId, version, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{pluginId}/versions/{version}/disable")
    @OperationLog(module = "连接器插件", operation = "禁用插件版本")
    public Result<ConnectorPluginVersionDTO> disable(@PathVariable String pluginId,
                                                      @PathVariable String version) {
        if (!allowed("connector-plugin:disable")) return forbidden();
        return Result.success(service.disable(pluginId, version, UserContext.getCurrentUserId()));
    }

    @ExceptionHandler(ConnectorConflictException.class)
    public ResponseEntity<Result<Void>> conflict(ConnectorConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(409, exception.getMessage()));
    }

    @ExceptionHandler(PluginArtifactValidationException.class)
    public ResponseEntity<Result<Void>> artifactValidation(PluginArtifactValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, exception.getMessage()));
    }

    private boolean allowed(String permission) {
        return UserContext.hasPermission(permission);
    }

    private <T> Result<T> forbidden() {
        return Result.error(403, "没有连接器插件操作权限");
    }
}
