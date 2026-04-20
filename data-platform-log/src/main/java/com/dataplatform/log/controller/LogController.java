package com.dataplatform.log.controller;

import com.dataplatform.common.pojo.ApiResponse;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.log.entity.OperationLog;
import com.dataplatform.log.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/log")
public class LogController {
    @Autowired
    private LogService logService;

    @GetMapping("/list")
    public ApiResponse<PageResponse<OperationLog>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<OperationLog> result = logService.list(username, module, operation, startTime, endTime, page, pageSize);
        return ApiResponse.success(result);
    }
}
