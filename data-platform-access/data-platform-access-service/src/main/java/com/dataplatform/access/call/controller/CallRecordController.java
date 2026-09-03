package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.service.CallRecordService;
import com.dataplatform.access.call.vo.InterfaceQualityVO;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 访问域数据调用的 Call Record Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/call-record")
public class CallRecordController {

    @Autowired
    private CallRecordService callRecordService;

    @GetMapping("/list")
    public PageResult<CallRecord> list(
            @RequestParam(required = false) Long callerId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String apiCode,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) Boolean cacheHit,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        if (!hasTenantScope()) {
            return deniedPage(page, pageSize);
        }
        return callRecordService.list(scopeTenant(), callerId, vendorId, dataType, success, apiCode, productCode,
                sceneCode, cacheHit, startTime, endTime, page, pageSize);
    }

    @PostMapping("/query")
    public ResponseEntity<Result<PageResult<CallRecord>>> query(@RequestBody Map<String, Object> queryParams) {
        if (queryParams == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "查询参数不能为空"));
        }
        if (!hasTenantScope()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(403, "当前用户没有调用记录租户作用域"));
        }
        try {
            int page = parseInteger(queryParams.get("page"), 1, "page", 1, Integer.MAX_VALUE);
            int pageSize = parseInteger(queryParams.get("pageSize"), 10, "pageSize", 1, 100);
            Long callerId = parseLong(queryParams.get("callerId"), "callerId");
            Long vendorId = parseLong(queryParams.get("vendorId"), "vendorId");
            String dataType = parseString(queryParams.get("dataType"), "dataType");
            Boolean success = parseBoolean(queryParams.get("success"), "success");

            return ResponseEntity.ok(Result.success(callRecordService.list(scopeTenant(), callerId, vendorId, dataType,
                    success, null, null, null, null, null, null, page, pageSize)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, exception.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<CallRecord>> getById(@PathVariable Long id) {
        CallRecord record = callRecordService.getById(id);
        if (record == null || !tenantAllowed(record)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "调用记录不存在"));
        }
        return ResponseEntity.ok(Result.success(record));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return hasTenantScope()
                ? Result.success(callRecordService.getStats(scopeTenant(), startTime, endTime))
                : Result.error(403, "当前用户没有调用记录租户作用域");
    }

    @GetMapping("/dimension-stats")
    public Result<Map<String, Object>> getDimensionStats(
            @RequestParam(required = false) Long callerId,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) String apiCode,
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) Boolean cacheHit,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return hasTenantScope()
                ? Result.success(callRecordService.getDimensionStats(scopeTenant(), callerId, productCode, sceneCode,
                apiCode, vendorCode, dataType, cacheHit, startTime, endTime))
                : Result.error(403, "当前用户没有调用记录租户作用域");
    }

    @GetMapping("/quality-report")
    public Result<List<InterfaceQualityVO>> getInterfaceQualityReport(
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) String apiCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return hasTenantScope()
                ? Result.success(callRecordService.getInterfaceQualityReport(scopeTenant(), vendorCode, dataType,
                apiCode, startTime, endTime))
                : Result.error(403, "当前用户没有调用记录租户作用域");
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long callerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        if (!hasTenantScope()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] data = callRecordService.exportData(scopeTenant(), callerId, startTime, endTime);
        String filename = "call-record-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(data.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }

    private boolean isPlatformAdmin() {
        return UserContext.hasPermission("system:admin");
    }

    private Long scopeTenant() {
        return isPlatformAdmin() ? null : UserContext.getCurrentTenantId();
    }

    private boolean hasTenantScope() {
        return isPlatformAdmin() || UserContext.getCurrentTenantId() != null;
    }

    private boolean tenantAllowed(CallRecord record) {
        return isPlatformAdmin()
                || (UserContext.getCurrentTenantId() != null
                && UserContext.getCurrentTenantId().equals(record.getTenantId()));
    }

    private int parseInteger(Object value, int defaultValue, String field, int min, int max) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value).trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(field + "参数不合法");
            }
            return parsed;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(field + "参数不合法");
        }
    }

    private Long parseLong(Object value, String field) {
        if (value == null) {
            return null;
        }
        try {
            long parsed = value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value).trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException(field + "参数不合法");
            }
            return parsed;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(field + "参数不合法");
        }
    }

    private String parseString(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(field + "参数不合法");
        }
        String parsed = ((String) value).trim();
        return parsed.isEmpty() ? null : parsed;
    }

    private Boolean parseBoolean(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue.trim())) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(stringValue.trim())) {
                return Boolean.FALSE;
            }
        }
        throw new IllegalArgumentException(field + "参数不合法");
    }

    private PageResult<CallRecord> deniedPage(int page, int pageSize) {
        PageResult<CallRecord> denied = new PageResult<>();
        denied.setCode(HttpStatus.FORBIDDEN.value());
        denied.setMessage("当前用户没有调用记录租户作用域");
        denied.setData(List.of());
        denied.setTotal(0L);
        denied.setPage(page);
        denied.setPageSize(pageSize);
        return denied;
    }
}
