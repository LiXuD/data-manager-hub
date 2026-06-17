package com.dataplatform.governance.log.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.governance.log.entity.OperationLog;
import com.dataplatform.governance.log.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}