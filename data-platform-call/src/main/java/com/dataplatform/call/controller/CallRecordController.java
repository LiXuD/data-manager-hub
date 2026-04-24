package com.dataplatform.call.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.call.entity.CallRecord;
import com.dataplatform.call.service.CallRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return callRecordService.list(callerId, vendorId, dataType, success, startTime, endTime, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<CallRecord>> getById(@PathVariable Long id) {
        CallRecord record = callRecordService.getById(id);
        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "调用记录不存在"));
        }
        return ResponseEntity.ok(Result.success(record));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(callRecordService.getStats(startTime, endTime));
    }

    @PostMapping("/query")
    public PageResult<CallRecord> query(@RequestBody Map<String, Object> params) {
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.get("pageSize") != null ? ((Number) params.get("pageSize")).intValue() : 10;

        Long callerId = params.get("callerId") != null ? ((Number) params.get("callerId")).longValue() : null;
        String phoneNumber = (String) params.get("phoneNumber");

        return callRecordService.query(callerId, phoneNumber, page, pageSize);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long callerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime endTime,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        byte[] data = callRecordService.exportData(callerId, startTime, endTime, format);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=call_records.csv")
                .body(data);
    }
}