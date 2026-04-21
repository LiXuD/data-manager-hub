package com.dataplatform.call.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.call.entity.CallRecord;
import com.dataplatform.call.service.CallRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
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
    public Result<CallRecord> getById(@PathVariable Long id) {
        CallRecord record = callRecordService.getById(id);
        if (record == null) {
            return Result.fail(404, "调用记录不存在");
        }
        return Result.success(record);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(callRecordService.getStats(startTime, endTime));
    }

    @GetMapping("/export")
    public Result<String> export(
            @RequestParam(required = false) Long callerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        String fileUrl = callRecordService.export(callerId, startTime, endTime);
        return Result.success(fileUrl);
    }
}