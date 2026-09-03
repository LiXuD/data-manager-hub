package com.dataplatform.masterdata.connector.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.ConnectorValidationResultDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorDraftDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorPublishRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRollbackRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorSaveDraftRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestResultDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorVersionDTO;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.ConnectorLegacyWriteRetiredException;
import com.dataplatform.masterdata.connector.service.VendorConnectorService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vendor/config")
public class VendorConnectorController {
    private final VendorConnectorService service;

    public VendorConnectorController(VendorConnectorService service) {
        this.service = service;
    }

    @GetMapping("/{configId}/connector")
    public Result<VendorConnectorVersionDTO> active(@PathVariable Long configId) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.active(configId));
    }

    @GetMapping("/{configId}/connector/draft")
    public Result<VendorConnectorDraftDTO> draft(@PathVariable Long configId) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.draft(configId));
    }

    @PutMapping("/{configId}/connector/draft")
    @OperationLog(module = "厂商连接器", operation = "保存连接器草稿")
    public Result<VendorConnectorDraftDTO> saveDraft(@PathVariable Long configId,
            @Valid @RequestBody VendorConnectorSaveDraftRequestDTO request) {
        if (!allowed("connector-plugin:bind")) return forbidden();
        if (request == null) return Result.error(400, "连接器草稿请求不能为空");
        return Result.success(service.saveDraft(configId, request, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{configId}/connector/validate")
    public Result<ConnectorValidationResultDTO> validate(@PathVariable Long configId) {
        if (!allowed("connector-plugin:bind")) return forbidden();
        return Result.success(service.validate(configId));
    }

    @PostMapping("/{configId}/connector/test")
    @OperationLog(module = "厂商连接器", operation = "受控测试连接器草稿",
            saveParams = false, saveResult = false)
    public Result<VendorConnectorTestResultDTO> test(@PathVariable Long configId,
            @RequestBody(required = false) VendorConnectorTestRequestDTO request) {
        if (!allowed("connector-plugin:test")) return forbidden();
        return Result.success(service.test(configId, request, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{configId}/connector/publish")
    @OperationLog(module = "厂商连接器", operation = "发布连接器版本")
    public Result<VendorConnectorVersionDTO> publish(@PathVariable Long configId,
            @Valid @RequestBody VendorConnectorPublishRequestDTO request) {
        if (!allowed("connector-plugin:publish")) return forbidden();
        if (request == null) return Result.error(400, "连接器发布请求不能为空");
        return Result.success(service.publish(configId, request.expectedDraftVersion(),
                UserContext.getCurrentUserId()));
    }

    @GetMapping("/{configId}/connector/versions")
    public Result<List<VendorConnectorVersionDTO>> versions(@PathVariable Long configId) {
        if (!allowed("connector-plugin:view")) return forbidden();
        return Result.success(service.history(configId));
    }

    @PostMapping("/{configId}/connector/rollback/{version}")
    @OperationLog(module = "厂商连接器", operation = "回滚连接器版本")
    public Result<VendorConnectorVersionDTO> rollback(@PathVariable Long configId,
            @PathVariable Integer version,
            @Valid @RequestBody VendorConnectorRollbackRequestDTO request) {
        if (!allowed("connector-plugin:rollback")) return forbidden();
        if (request == null) return Result.error(400, "连接器回滚请求不能为空");
        return Result.success(service.rollback(configId, version, request.expectedConnectorVersion(),
                UserContext.getCurrentUserId()));
    }

    @ExceptionHandler(ConnectorConflictException.class)
    public ResponseEntity<Result<Void>> conflict(ConnectorConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(409, exception.getMessage()));
    }

    @ExceptionHandler(ConnectorLegacyWriteRetiredException.class)
    public ResponseEntity<Result<Void>> retired(ConnectorLegacyWriteRetiredException exception) {
        return ResponseEntity.status(HttpStatus.GONE).body(Result.error(410, exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, exception.getMessage()));
    }

    private boolean allowed(String permission) {
        return UserContext.hasPermission(permission)
                || UserContext.hasPermission("system:admin");
    }
    private <T> Result<T> forbidden() { return Result.error(403, "没有厂商连接器操作权限"); }
}
