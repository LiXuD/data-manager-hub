package com.dataplatform.governance.log.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.governance.log.entity.OperationLog;
import com.dataplatform.governance.log.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 观测治理域操作日志的 Log Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/log")
public class LogController {
    @Autowired
    private LogService logService;

    @GetMapping("/list")
    public PageResult<OperationLog> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return logService.list(keyword, module, operation, startTime, endTime, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<OperationLog>> get(@PathVariable Long id) {
        OperationLog log = logService.getById(id);
        if (log == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "日志不存在"));
        }
        return ResponseEntity.ok(Result.success(log));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.success(logService.stats(startTime, endTime));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        List<OperationLog> logs = logService.export(module, operation, startTime, endTime);
        StringBuilder csv = new StringBuilder("ID,操作人,模块,操作,方法,IP,耗时(ms),状态,操作时间\n");
        for (OperationLog log : logs) {
            csv.append(log.getId()).append(',')
                    .append(csvCell(log.getUsername())).append(',')
                    .append(csvCell(log.getModule())).append(',')
                    .append(csvCell(log.getOperation())).append(',')
                    .append(csvCell(log.getMethod())).append(',')
                    .append(csvCell(log.getIp())).append(',')
                    .append(log.getDuration() == null ? "" : log.getDuration()).append(',')
                    .append(csvCell(log.getStatus())).append(',')
                    .append(log.getCreatedAt() == null ? "" : log.getCreatedAt())
                    .append('\n');
        }
        byte[] content = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operation-logs.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content);
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
