package com.dataplatform.log.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.log.entity.OperationLog;
import com.dataplatform.log.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public Result<OperationLog> get(@PathVariable Long id) {
        return Result.success(logService.getById(id));
    }
}