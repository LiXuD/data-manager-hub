package com.dataplatform.masterdata.connector.spec;

import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.ConnectorExecutionPlanDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecCatalogDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConversionPreviewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConvertRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDraftViewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecHistoryDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecPublishRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecRollbackRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecSaveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecTestRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecValidationDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecVersionDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestResultDTO;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vendor/config/{configId}/connector-spec")
public class ConnectorSpecController {
    private final ConnectorSpecService service;

    public ConnectorSpecController(ConnectorSpecService service) { this.service = service; }

    @GetMapping("/catalog")
    @OperationLog(module = "厂商连接器产品配置", operation = "查看连接器插件目录",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecCatalogDTO> catalog(@PathVariable Long configId) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.catalog(configId));
    }

    @GetMapping("/catalog/{pluginId}/versions")
    @OperationLog(module = "厂商连接器产品配置", operation = "查看连接器插件版本",
            saveParams = false, saveResult = false)
    public Result<List<ConnectorSpecCatalogDTO.Version>> versions(
            @PathVariable Long configId, @PathVariable String pluginId) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.versions(configId, pluginId));
    }

    @GetMapping("/draft")
    @OperationLog(module = "厂商连接器产品配置", operation = "查看简化连接器草稿",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecDraftViewDTO> draft(@PathVariable Long configId) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.draft(configId));
    }

    @PutMapping("/draft")
    @OperationLog(module = "厂商连接器产品配置", operation = "保存简化连接器草稿",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecDraftViewDTO> saveDraft(
            @PathVariable Long configId, @RequestBody ConnectorSpecSaveRequestDTO request) {
        if (!allowed("connector-plugin:bind")) return forbidden();
        return Result.success(service.saveDraft(configId, request, UserContext.getCurrentUserId()));
    }

    @PostMapping("/validate")
    @OperationLog(module = "厂商连接器产品配置", operation = "校验简化连接器草稿",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecValidationDTO> validate(@PathVariable Long configId) {
        if (!allowed("connector-plugin:bind")) return forbidden();
        return Result.success(service.validate(configId));
    }

    @GetMapping("/execution-plan")
    @OperationLog(module = "厂商连接器产品配置", operation = "查看连接器执行计划",
            saveParams = false, saveResult = false)
    public Result<ConnectorExecutionPlanDTO> executionPlan(
            @PathVariable Long configId,
            @RequestParam(value = "version", required = false) Integer version) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.executionPlan(configId, version));
    }

    @PostMapping("/test")
    @OperationLog(module = "厂商连接器产品配置", operation = "受控测试简化连接器草稿",
            saveParams = false, saveResult = false)
    public Result<VendorConnectorTestResultDTO> test(
            @PathVariable Long configId, @RequestBody ConnectorSpecTestRequestDTO request) {
        if (!allowed("connector-plugin:test")) return forbidden();
        return Result.success(service.test(configId, request, UserContext.getCurrentUserId()));
    }

    @PostMapping("/publish")
    @OperationLog(module = "厂商连接器产品配置", operation = "发布简化连接器草稿",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecVersionDTO> publish(
            @PathVariable Long configId, @RequestBody ConnectorSpecPublishRequestDTO request) {
        if (!allowed("connector-plugin:publish")) return forbidden();
        return Result.success(service.publish(configId, request, UserContext.getCurrentUserId()));
    }

    @GetMapping("/versions")
    @OperationLog(module = "厂商连接器产品配置", operation = "查看连接器历史版本",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecHistoryDTO> history(@PathVariable Long configId) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.history(configId));
    }

    @PostMapping("/rollback/{version}")
    @OperationLog(module = "厂商连接器产品配置", operation = "回滚连接器历史版本",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecVersionDTO> rollback(
            @PathVariable Long configId, @PathVariable Integer version,
            @RequestBody ConnectorSpecRollbackRequestDTO request) {
        if (!allowed("connector-plugin:rollback")) return forbidden();
        return Result.success(service.rollback(
                configId, version, request, UserContext.getCurrentUserId()));
    }

    @PostMapping("/upgrade-preview")
    @OperationLog(module = "厂商连接器产品配置", operation = "预检连接器插件版本升级",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecUpgradePreviewDTO> upgradePreview(
            @PathVariable Long configId,
            @RequestBody ConnectorSpecUpgradePreviewRequestDTO request) {
        if (!allowed("connector-plugin:bind")) return forbidden();
        return Result.success(service.upgradePreview(configId, request));
    }

    @PostMapping("/convert-preview")
    @OperationLog(module = "厂商连接器产品配置", operation = "预检 Legacy 连接器草稿转换",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecConversionPreviewDTO> convertPreview(@PathVariable Long configId) {
        if (!allowed("connector-plugin:bind")) return forbidden();
        return Result.success(service.convertPreview(configId));
    }

    @PostMapping("/convert")
    @OperationLog(module = "厂商连接器产品配置", operation = "转换 Legacy 连接器草稿",
            saveParams = false, saveResult = false)
    public Result<ConnectorSpecDraftViewDTO> convert(
            @PathVariable Long configId, @RequestBody ConnectorSpecConvertRequestDTO request) {
        if (!allowed("connector-plugin:bind")) return forbidden();
        return Result.success(service.convert(configId, request, UserContext.getCurrentUserId()));
    }

    @ExceptionHandler(ConnectorConflictException.class)
    public ResponseEntity<Result<Void>> conflict(ConnectorConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(409, exception.getMessage()));
    }

    @ExceptionHandler(LegacyPipelineNotConvertibleException.class)
    public ResponseEntity<Result<ConnectorSpecConversionPreviewDTO>> notConvertible(
            LegacyPipelineNotConvertibleException exception) {
        Result<ConnectorSpecConversionPreviewDTO> result = Result.error(
                409, "LEGACY_PIPELINE_NOT_CONVERTIBLE");
        result.setData(exception.preview());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    @ExceptionHandler(ConnectorSpecNotFoundException.class)
    public ResponseEntity<Result<Void>> notFound(ConnectorSpecNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error(404, exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, exception.getMessage()));
    }

    private boolean allowed(String permission) { return UserContext.hasPermission(permission); }
    private <T> Result<T> forbidden() { return Result.error(403, "没有厂商连接器操作权限"); }
}
