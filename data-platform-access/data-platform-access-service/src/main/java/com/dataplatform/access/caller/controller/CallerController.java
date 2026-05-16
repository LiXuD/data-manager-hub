package com.dataplatform.access.caller.controller;

import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
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

    @OperationLog(module = "调用方管理", operation = "新增调用方")
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
        caller.setStatus(CommonStatus.ACTIVE);
        callerService.save(caller);
        return ResponseEntity.ok(Result.success(caller));
    }

    @OperationLog(module = "调用方管理", operation = "更新调用方")
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

    @OperationLog(module = "调用方管理", operation = "删除调用方")
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

    @OperationLog(module = "API Key管理", operation = "新增API Key")
    @PostMapping("/{callerId}/api-key")
    public ResponseEntity<Result<Map<String, Object>>> createApiKey(@PathVariable Long callerId, @RequestBody Map<String, Object> params) {
        CallerInfo caller = callerService.getById(callerId);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }

        String name = (String) params.getOrDefault("name", params.get("keyName"));
        if (name == null || name.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "name不能为空"));
        }

        ApiKey apiKey = apiKeyService.createApiKey(callerId, name);

        Map<String, Object> result = new HashMap<>();
        result.put("id", apiKey.getId());
        result.put("callerId", callerId);
        result.put("keyName", name);
        result.put("status", ApiKeyStatus.ACTIVE.getCode());

        return ResponseEntity.ok(Result.success(result));
    }

    @OperationLog(module = "API Key管理", operation = "更新API Key状态")
    @PatchMapping("/api-key/{id}/status")
    public ResponseEntity<Result<Void>> updateApiKeyStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        ApiKeyStatus statusEnum = ApiKeyStatus.fromCode(status);
        if (statusEnum == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "status必须是有效的API Key状态"));
        }

        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "API Key不存在"));
        }
        apiKey.setStatus(statusEnum);
        apiKeyService.updateById(apiKey);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "调用方管理", operation = "更新调用方状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        CommonStatus statusEnum = CommonStatus.fromCode(status);
        if (statusEnum == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值，必须是active或inactive"));
        }

        CallerInfo existing = callerService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "调用方不存在"));
        }

        CallerInfo caller = new CallerInfo();
        caller.setId(id);
        caller.setStatus(statusEnum);
        callerService.updateById(caller);
        return ResponseEntity.ok(Result.success(null));
    }
}