package com.dataplatform.caller.controller;

import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.caller.entity.ApiKey;
import com.dataplatform.caller.entity.CallerInfo;
import com.dataplatform.caller.service.ApiKeyService;
import com.dataplatform.caller.service.CallerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/caller")
public class CallerController {

    @Autowired
    private CallerService callerService;

    @Autowired
    private ApiKeyService apiKeyService;

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
        caller.setStatus(StatusConstants.ACTIVE);
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

    // ==================== API Key 管理 ====================

    @PostMapping("/{callerId}/api-key")
    public ResponseEntity<Result<Map<String, Object>>> createApiKey(@PathVariable Long callerId, @RequestBody Map<String, Object> params) {
        CallerInfo caller = callerService.getById(callerId);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }

        String name = (String) params.getOrDefault("name", params.get("keyName"));
        if (name == null || name.toString().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "name不能为空"));
        }

        ApiKey apiKey = new ApiKey();
        apiKey.setCallerId(callerId);
        apiKey.setKeyName(name);
        apiKey.setStatus(StatusConstants.ACTIVE);
        apiKeyService.save(apiKey);

        Map<String, Object> result = new HashMap<>();
        result.put("id", apiKey.getId());
        result.put("callerId", callerId);
        result.put("keyName", name);
        result.put("status", StatusConstants.ACTIVE);

        return ResponseEntity.ok(Result.success(result));
    }

    @PatchMapping("/api-key/{id}/status")
    public ResponseEntity<Result<Void>> updateApiKeyStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || (!status.equals(StatusConstants.ACTIVE) && !status.equals(StatusConstants.INACTIVE))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "status必须是active或inactive"));
        }

        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "API Key不存在"));
        }
        apiKey.setStatus(status);
        apiKeyService.updateById(apiKey);
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
        if (!status.equals(StatusConstants.ACTIVE) && !status.equals(StatusConstants.INACTIVE) && !status.equals(StatusConstants.SUSPENDED)) {
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