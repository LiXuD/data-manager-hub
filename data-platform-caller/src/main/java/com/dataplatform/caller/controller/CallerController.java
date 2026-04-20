package com.dataplatform.caller.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.caller.entity.CallerInfo;
import com.dataplatform.caller.service.CallerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/caller")
public class CallerController {
    
    @Autowired
    private CallerService callerService;
    
    @GetMapping("/list")
    public PageResult<CallerInfo> list(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {
        return callerService.list(page, pageSize, keyword, status);
    }
    
    @GetMapping("/{id}")
    public Result<CallerInfo> getById(@PathVariable Long id) {
        return Result.success(callerService.getById(id));
    }
}