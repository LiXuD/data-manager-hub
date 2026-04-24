package com.dataplatform.caller.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.caller.entity.CallerInfo;
import com.dataplatform.caller.service.CallerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Result<CallerInfo>> getById(@PathVariable Long id) {
        CallerInfo caller = callerService.getById(id);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        return ResponseEntity.ok(Result.success(caller));
    }

    @PostMapping
    public ResponseEntity<Result<CallerInfo>> create(@RequestBody CallerInfo caller) {
        // 校验必填字段
        if (caller.getCallerName() == null || caller.getCallerName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "调用方名称不能为空"));
        }

        // 检查重复 (使用callerCode)
        CallerInfo existing = callerService.getByCode(caller.getCallerCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "调用方代码已存在"));
        }

        caller.setId(null);
        caller.setStatus("active");
        callerService.save(caller);
        return ResponseEntity.ok(Result.success(caller));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<CallerInfo>> update(@PathVariable Long id, @RequestBody CallerInfo caller) {
        // 检查是否存在
        CallerInfo existing = callerService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        caller.setId(id);
        callerService.updateById(caller);
        return ResponseEntity.ok(Result.success(callerService.getById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        // 检查是否存在
        CallerInfo existing = callerService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }
        callerService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        // 校验status有效性
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "状态不能为空"));
        }
        if (!status.equals("active") && !status.equals("inactive") && !status.equals("suspended")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值"));
        }

        // 检查是否存在
        CallerInfo existing = callerService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }

        CallerInfo caller = new CallerInfo();
        caller.setId(id);
        caller.setStatus(status);
        callerService.updateById(caller);
        return ResponseEntity.ok(Result.success(null));
    }
}