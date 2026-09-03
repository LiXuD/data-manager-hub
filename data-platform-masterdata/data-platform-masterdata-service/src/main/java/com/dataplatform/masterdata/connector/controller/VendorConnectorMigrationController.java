package com.dataplatform.masterdata.connector.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationActionRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationObserveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationRepairCandidateDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationStartRequestDTO;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.VendorConnectorMigrationService;
import com.dataplatform.masterdata.connector.spec.ConnectorSpecNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controlled vendor-by-vendor migration and observation control plane. */
@RestController
@RequestMapping("/vendor/connector-migration")
public class VendorConnectorMigrationController {
    private final VendorConnectorMigrationService service;

    public VendorConnectorMigrationController(VendorConnectorMigrationService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<VendorConnectorMigrationDTO>> list(
            @RequestParam(required = false) String state) {
        if (!UserContext.hasPermission("connector-plugin:view")
                && !UserContext.hasPermission("system:admin")) {
            return Result.error(403, "没有厂商连接器迁移记录查看权限");
        }
        return Result.success(service.list(state));
    }

    @GetMapping("/prepared-audit")
    public Result<List<VendorConnectorMigrationRepairCandidateDTO>> preparedAudit() {
        if (!UserContext.hasPermission("connector-plugin:view")
                && !UserContext.hasPermission("system:admin")) {
            return Result.error(403, "没有厂商连接器迁移记录查看权限");
        }
        return Result.success(service.auditInvalidPrepared());
    }

    @PostMapping("/repair-invalid-prepared")
    @OperationLog(module = "厂商连接器迁移", operation = "修复无效迁移准备记录",
            saveParams = false, saveResult = false)
    public Result<Integer> repairInvalidPrepared() {
        if (!allowed()) return forbidden();
        return Result.success(service.repairInvalidPrepared(UserContext.getCurrentUserId()));
    }

    @PostMapping("/{vendorConfigId}/prepare")
    @OperationLog(module = "厂商连接器迁移", operation = "准备厂商连接器迁移",
            saveParams = false, saveResult = false)
    public Result<VendorConnectorMigrationDTO> prepare(@PathVariable Long vendorConfigId) {
        if (!allowed()) return forbidden();
        return Result.success(service.prepare(vendorConfigId, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{vendorConfigId}/start-observation")
    @OperationLog(module = "厂商连接器迁移", operation = "开始连接器稳定观察",
            saveParams = false, saveResult = false)
    public Result<VendorConnectorMigrationDTO> startObservation(
            @PathVariable Long vendorConfigId,
            @RequestBody VendorConnectorMigrationStartRequestDTO request) {
        if (!allowed()) return forbidden();
        return Result.success(service.startObservation(
                vendorConfigId, request, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{vendorConfigId}/observe")
    @OperationLog(module = "厂商连接器迁移", operation = "刷新连接器稳定观察",
            saveParams = false, saveResult = false)
    public Result<VendorConnectorMigrationDTO> observe(
            @PathVariable Long vendorConfigId,
            @RequestBody VendorConnectorMigrationObserveRequestDTO request) {
        if (!allowed()) return forbidden();
        return Result.success(service.observe(vendorConfigId, request, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{vendorConfigId}/complete")
    @OperationLog(module = "厂商连接器迁移", operation = "完成连接器稳定观察",
            saveParams = false, saveResult = false)
    public Result<VendorConnectorMigrationDTO> complete(
            @PathVariable Long vendorConfigId,
            @RequestBody VendorConnectorMigrationActionRequestDTO request) {
        if (!allowed()) return forbidden();
        return Result.success(service.complete(vendorConfigId, request, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{vendorConfigId}/rollback")
    @OperationLog(module = "厂商连接器迁移", operation = "回滚连接器迁移",
            saveParams = false, saveResult = false)
    public Result<VendorConnectorMigrationDTO> rollback(
            @PathVariable Long vendorConfigId,
            @RequestBody VendorConnectorMigrationActionRequestDTO request) {
        if (!allowed()) return forbidden();
        return Result.success(service.rollback(vendorConfigId, request, UserContext.getCurrentUserId()));
    }

    @ExceptionHandler(ConnectorConflictException.class)
    public ResponseEntity<Result<Void>> conflict(ConnectorConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(409, exception.getMessage()));
    }

    @ExceptionHandler(ConnectorSpecNotFoundException.class)
    public ResponseEntity<Result<Void>> notFound(ConnectorSpecNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error(404, exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, exception.getMessage()));
    }

    private boolean allowed() {
        return UserContext.hasPermission("connector-plugin:migrate")
                || UserContext.hasPermission("system:admin");
    }

    private <T> Result<T> forbidden() { return Result.error(403, "没有厂商连接器迁移操作权限"); }
}
