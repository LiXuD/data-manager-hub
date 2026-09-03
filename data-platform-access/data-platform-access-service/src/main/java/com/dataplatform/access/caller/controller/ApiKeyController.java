package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyProductService;
import com.dataplatform.access.caller.service.ApiKeyProvisioningService;
import com.dataplatform.access.caller.service.ApiKeyProvisioningException;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.access.caller.service.CurrentUserApiKeyOptionService;
import com.dataplatform.access.caller.vo.ApiKeyCreateReqVO;
import com.dataplatform.access.caller.vo.ApiKeyRateLimitUpdateVO;
import com.dataplatform.access.caller.vo.ApiKeyResponse;
import com.dataplatform.access.caller.vo.CurrentUserApiKeyOptionsVO;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private ApiKeyProvisioningService apiKeyProvisioningService;
    @Autowired
    private CallerProductService callerProductService;
    @Autowired
    private CallerService callerService;
    @Autowired
    private CurrentUserApiKeyOptionService currentUserApiKeyOptionService;

    @GetMapping("/list")
    public Result<List<ApiKeyResponse>> list(@RequestParam(value = "callerId", required = false) Long callerId) {
        if (callerId != null) {
            if (!callerAllowed(callerId)) {
                return Result.error(404, "调用方不存在");
            }
            return Result.success(toViews(apiKeyService.listByCaller(callerId)));
        }
        if (isPlatformAdmin()) {
            return Result.success(toViews(apiKeyService.list()));
        }
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) {
            return Result.error(403, "当前用户没有租户作用域");
        }
        List<ApiKey> keys = new ArrayList<>();
        callerService.listAllByTenant(tenantId).forEach(caller -> keys.addAll(apiKeyService.listByCaller(caller.getId())));
        return Result.success(toViews(keys));
    }

    @GetMapping("/current-user-options")
    public Result<CurrentUserApiKeyOptionsVO> currentUserOptions() {
        return Result.success(currentUserApiKeyOptionService.listOptions(
                UserContext.getCurrentUserId(), UserContext.getCurrentTenantId()));
    }

    @GetMapping("/{id}")
    public Result<ApiKeyResponse> getById(@PathVariable Long id) {
        ApiKey apiKey = ownedApiKey(id);
        return apiKey == null ? Result.error(404, "API Key不存在") : Result.success(ApiKeyResponse.view(apiKey));
    }

    @OperationLog(module = "API Key管理", operation = "新增API Key")
    @PostMapping
    public ResponseEntity<Result<ApiKeyResponse>> create(@RequestBody ApiKeyCreateReqVO request) {
        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "请求体不能为空"));
        }
        Long callerId = request.getCallerId();
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "callerId不能为空"));
        }
        String name = request.getName();
        if (name == null || name.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "name不能为空"));
        }
        if (!callerAllowed(callerId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "调用方不存在"));
        }
        String productError = validateProductIds(callerId, request.getProductIds(), true);
        if (productError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, productError));
        }

        try {
            ApiKey apiKey = apiKeyProvisioningService.create(callerId, name.trim(), request.getProductIds());
            return ResponseEntity.ok(Result.success(ApiKeyResponse.created(apiKey)));
        } catch (ApiKeyProvisioningException exception) {
            return error(exception);
        }
    }

    @OperationLog(module = "API Key管理", operation = "更新API Key状态")
    @PutMapping("/{id}/status")
    public Result<ApiKeyResponse> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        if (params == null) {
            return Result.error(400, "请求体不能为空");
        }
        Object rawStatus = params.get("status");
        if (!(rawStatus instanceof String)) {
            return Result.error(400, "status必须是字符串，且取值为active、expired或revoked");
        }
        String status = ((String) rawStatus).trim();
        ApiKeyStatus statusEnum = ApiKeyStatus.fromCode(status);
        if (statusEnum == null) {
            return Result.error(400, "status必须是active、expired或revoked");
        }
        ApiKey apiKey = ownedApiKey(id);
        if (apiKey == null) {
            return Result.error(404, "API Key不存在");
        }
        ApiKey patch = new ApiKey();
        patch.setId(id);
        patch.setStatus(statusEnum);
        if (!apiKeyService.updateById(patch)) {
            return Result.error(409, "API Key状态已被其他请求修改，请刷新后重试");
        }
        ApiKey latest = apiKeyService.getById(id);
        return Result.success(ApiKeyResponse.view(latest != null ? latest : patch));
    }

    @OperationLog(module = "API Key管理", operation = "更新限流策略")
    @PutMapping("/{id}/rate-limit")
    public ResponseEntity<Result<ApiKeyResponse>> updateRateLimit(
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

        if (ownedApiKey(id) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }

        ApiKey apiKey = apiKeyService.updateRateLimitPolicy(
                id, request.getRateLimitEnabled(), request.getRateLimit());
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        return ResponseEntity.ok(Result.success(ApiKeyResponse.view(apiKey)));
    }

    @GetMapping("/{id}/interfaces")
    public ResponseEntity<Result<List<Long>>> getInterfaceIds(@PathVariable Long id) {
        ApiKey apiKey = ownedApiKey(id);
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
        ApiKey apiKey = ownedApiKey(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(
                        HttpStatus.CONFLICT.value(),
                        "接口权限已启用审批，请通过 /api/v1/api-permission/applications 提交申请"));
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<Result<List<Long>>> getProductIds(@PathVariable Long id) {
        ApiKey apiKey = ownedApiKey(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        return ResponseEntity.ok(Result.success(apiKeyProductService.getProductIdsByApiKeyId(id)));
    }

    @OperationLog(module = "API Key管理", operation = "分配产品权限")
    @PostMapping("/{id}/products")
    public ResponseEntity<Result<Void>> assignProducts(@PathVariable Long id, @RequestBody List<Long> productIds) {
        ApiKey apiKey = ownedApiKey(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        String productError = validateProductIds(apiKey.getCallerId(), productIds, false);
        if (productError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, productError));
        }
        try {
            apiKeyProductService.assignProducts(id, productIds);
            return ResponseEntity.ok(Result.success(null));
        } catch (ApiKeyProvisioningException exception) {
            return error(exception);
        }
    }

    private String validateProductIds(Long callerId, List<Long> productIds, boolean required) {
        if (productIds == null || productIds.isEmpty()) {
            return required ? "请至少选择一个产品" : null;
        }
        if (productIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(productIds).size() != productIds.size()) {
            return "产品列表包含无效或重复数据";
        }
        List<CallerProduct> products = callerProductService.listByIds(productIds);
        if (products == null || products.size() != productIds.size()
                || products.stream().anyMatch(product -> product == null
                || !java.util.Objects.equals(callerId, product.getCallerId()))) {
            return "产品必须属于该API Key对应调用方";
        }
        if (products.stream().anyMatch(product -> !StatusConstants.ACTIVE.equals(product.getStatus()))) {
            return "只能授权启用状态的产品";
        }
        return null;
    }

    @OperationLog(module = "API Key管理", operation = "删除API Key")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        ApiKey apiKey = ownedApiKey(id);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "API Key不存在"));
        }
        if (!apiKeyService.removeById(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "API Key删除失败，请重试"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    private boolean isPlatformAdmin() {
        return UserContext.hasPermission("system:admin");
    }

    private boolean callerAllowed(Long callerId) {
        CallerInfo caller = callerId == null ? null : callerService.getById(callerId);
        return caller != null && (isPlatformAdmin()
                || (UserContext.getCurrentTenantId() != null
                && UserContext.getCurrentTenantId().equals(caller.getTenantId())));
    }

    private ApiKey ownedApiKey(Long id) {
        ApiKey apiKey = id == null ? null : apiKeyService.getById(id);
        if (apiKey == null || !callerAllowed(apiKey.getCallerId())) {
            return null;
        }
        return apiKey;
    }

    private List<ApiKeyResponse> toViews(List<ApiKey> keys) {
        if (keys == null) {
            return List.of();
        }
        return keys.stream()
                .filter(Objects::nonNull)
                .map(ApiKeyResponse::view)
                .toList();
    }

    private <T> ResponseEntity<Result<T>> error(ApiKeyProvisioningException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Result.error(exception.getStatus().value(),
                        exception.getErrorCode() + ": " + exception.getMessage()));
    }
}
