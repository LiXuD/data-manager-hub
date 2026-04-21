package com.dataplatform.caller.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.caller.entity.CallerInfo;
import com.dataplatform.caller.service.CallerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        CallerInfo caller = callerService.getById(id);
        if (caller == null) {
            return Result.fail(404, "调用方不存在");
        }
        return Result.success(caller);
    }

    @PostMapping
    public Result<CallerInfo> create(@RequestBody CallerInfo caller) {
        caller.setId(null);
        caller.setStatus("active");
        callerService.save(caller);
        return Result.success(caller);
    }

    @PutMapping("/{id}")
    public Result<CallerInfo> update(@PathVariable Long id, @RequestBody CallerInfo caller) {
        caller.setId(id);
        callerService.updateById(caller);
        return Result.success(callerService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        callerService.removeById(id);
        return Result.success(null);
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        CallerInfo caller = new CallerInfo();
        caller.setId(id);
        caller.setStatus(status);
        callerService.updateById(caller);
        return Result.success(null);
    }
}