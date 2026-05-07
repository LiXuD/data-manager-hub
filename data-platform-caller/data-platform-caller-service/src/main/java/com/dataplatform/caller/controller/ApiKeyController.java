package com.dataplatform.caller.controller;

import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.caller.entity.ApiKey;
import com.dataplatform.caller.service.ApiKeyInterfaceService;
import com.dataplatform.caller.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/caller/apikey")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ApiKeyInterfaceService apiKeyInterfaceService;

    @GetMapping("/list")
    public Result<List<ApiKey>> list() {
        return Result.success(apiKeyService.list());
    }

    @GetMapping("/{id}")
    public Result<ApiKey> getById(@PathVariable Long id) {
        return Result.success(apiKeyService.getById(id));
    }

    @OperationLog(module = "API Key管理", operation = "新增API Key")
    @PostMapping("/{callerId}")
    public ResponseEntity<Result<ApiKey>> create(@PathVariable Long callerId, @RequestBody Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "name不能为空"));
        }

        ApiKey apiKey = new ApiKey();
        apiKey.setCallerId(callerId);
        apiKey.setKeyName(name);
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKeyService.save(apiKey);
        return ResponseEntity.ok(Result.success(apiKeyService.getById(apiKey.getId())));
    }

    @OperationLog(module = "API Key管理", operation = "新增API Key")
    @PostMapping("/{callerId}/api-key")
    public ResponseEntity<Result<ApiKey>> createApiKey(@PathVariable Long callerId, @RequestBody Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "name不能为空"));
        }

        ApiKey apiKey = new ApiKey();
        apiKey.setCallerId(callerId);
        apiKey.setKeyName(name);
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKeyService.save(apiKey);
        return ResponseEntity.ok(Result.success(apiKeyService.getById(apiKey.getId())));
    }

    @OperationLog(module = "API Key管理", operation = "更新API Key状态")
    @PutMapping("/api-key/{id}/status")
    public Result<ApiKey> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String status = (String) params.get("status");
        ApiKeyStatus statusEnum = ApiKeyStatus.fromCode(status);
        if (statusEnum == null) {
            return Result.error(400, "status必须是active、expired或revoked");
        }
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return Result.error(404, "API Key不存在");
        }
        apiKey.setStatus(statusEnum);
        apiKeyService.updateById(apiKey);
        return Result.success(apiKey);
    }

    @GetMapping("/{id}/interfaces")
    public ResponseEntity<Result<List<Long>>> getInterfaceIds(@PathVariable Long id) {
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        List<Long> interfaceIds = apiKeyInterfaceService.getInterfaceIdsByApiKeyId(id);
        return ResponseEntity.ok(Result.success(interfaceIds));
    }

    @OperationLog(module = "API Key管理", operation = "分配接口权限")
    @PostMapping("/{id}/interfaces")
    public ResponseEntity<Result<Void>> assignInterfaces(@PathVariable Long id, @RequestBody List<Long> interfaceIds) {
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        apiKeyInterfaceService.assignInterfaces(id, interfaceIds);
        return ResponseEntity.ok(Result.success(null));
    }
}