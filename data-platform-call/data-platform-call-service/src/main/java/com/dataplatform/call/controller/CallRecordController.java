package com.dataplatform.call.controller;

import com.dataplatform.call.service.CallRecordService;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
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

    @PostMapping("/query")
    public ResponseEntity<Result<PageResult<CallRecord>>> query(@RequestBody Map<String, Object> queryParams) {
        int page = queryParams.get("page") != null ? ((Number) queryParams.get("page")).intValue() : 1;
        int pageSize = queryParams.get("pageSize") != null ? ((Number) queryParams.get("pageSize")).intValue() : 10;

        if (page < 1 || pageSize < 1 || pageSize > 100) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "分页参数不合法"));
        }

        Long callerId = queryParams.get("callerId") != null ? ((Number) queryParams.get("callerId")).longValue() : null;
        Long vendorId = queryParams.get("vendorId") != null ? ((Number) queryParams.get("vendorId")).longValue() : null;
        String dataType = (String) queryParams.get("dataType");
        Boolean success = (Boolean) queryParams.get("success");

        return ResponseEntity.ok(Result.success(callRecordService.list(callerId, vendorId, dataType, success, null, null, page, pageSize)));
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

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long callerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        byte[] data = callRecordService.exportData(callerId, startTime, endTime);
        String filename = "call-record-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(data.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }
}
