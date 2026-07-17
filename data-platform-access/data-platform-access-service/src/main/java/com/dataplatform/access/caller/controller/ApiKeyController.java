package com.dataplatform.access.caller.controller;

import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.ApiKeyProductService;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.vo.ApiKeyRateLimitUpdateVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 访问域调用方的 Api Key Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/caller/apikey")
public class ApiKeyController {

    private static final int MAX_RATE_LIMIT_PER_MINUTE = 1_000_000;

    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ApiKeyInterfaceService apiKeyInterfaceService;
    @Autowired
    private ApiKeyProductService apiKeyProductService;
    @Autowired
    private CallerProductService callerProductService;

    @GetMapping("/list")
    public Result<List<ApiKey>> list(@RequestParam(value = "callerId", required = false) Long callerId) {
        return Result.success(callerId != null ? apiKeyService.listByCaller(callerId) : apiKeyService.list());
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

        ApiKey apiKey = apiKeyService.createApiKey(callerId, name);
        return ResponseEntity.ok(Result.success(apiKey));
    }

    @OperationLog(module = "API Key管理", operation = "新增API Key")
    @PostMapping("/{callerId}/api-key")
    public ResponseEntity<Result<ApiKey>> createApiKey(@PathVariable Long callerId, @RequestBody Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "name不能为空"));
        }

        ApiKey apiKey = apiKeyService.createApiKey(callerId, name);
        return ResponseEntity.ok(Result.success(apiKey));
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

    @OperationLog(module = "API Key管理", operation = "更新限流策略")
    @PutMapping("/{id}/rate-limit")
    public ResponseEntity<Result<ApiKey>> updateRateLimit(
            @PathVariable Long id,
            @RequestBody ApiKeyRateLimitUpdateVO request) {
        if (request == null || request.getRateLimitEnabled() == null || request.getRateLimit() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "限流开关和每分钟最大请求数不能为空"));
        }
        if (request.getRateLimit() < 1 || request.getRateLimit() > MAX_RATE_LIMIT_PER_MINUTE) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "每分钟最大请求数必须在1到1000000之间"));
        }

        ApiKey apiKey = apiKeyService.updateRateLimitPolicy(
                id, request.getRateLimitEnabled(), request.getRateLimit());
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        return ResponseEntity.ok(Result.success(apiKey));
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

    @GetMapping("/{id}/products")
    public ResponseEntity<Result<List<Long>>> getProductIds(@PathVariable Long id) {
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        return ResponseEntity.ok(Result.success(apiKeyProductService.getProductIdsByApiKeyId(id)));
    }

    @OperationLog(module = "API Key管理", operation = "分配产品权限")
    @PostMapping("/{id}/products")
    public ResponseEntity<Result<Void>> assignProducts(@PathVariable Long id, @RequestBody List<Long> productIds) {
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        if (productIds != null && !productIds.isEmpty()) {
            List<CallerProduct> products = callerProductService.listByIds(productIds);
            if (products.size() != productIds.size()
                    || products.stream().anyMatch(product -> !apiKey.getCallerId().equals(product.getCallerId()))) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Result.error(400, "产品必须属于该API Key对应调用方"));
            }
        }
        apiKeyProductService.assignProducts(id, productIds);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "API Key管理", operation = "删除API Key")
    @DeleteMapping("/api-key/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        apiKeyService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }
}
