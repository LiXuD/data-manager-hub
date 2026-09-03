package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.service.CallSceneService;
import com.dataplatform.access.call.vo.DataTestOptionsVO;
import com.dataplatform.access.call.vo.DataTestQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyProductService;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CurrentUserApiKeyOptionService;
import com.dataplatform.access.caller.vo.CurrentUserApiKeyOptionVO;
import com.dataplatform.access.caller.vo.CurrentUserApiKeyOptionsVO;
import com.dataplatform.api.Result;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 登录态数据查询测试入口。服务端解析用户可选的 API Key，并复用 OpenAPI 查询链路。
 */
@RestController
@RequestMapping("/data-test")
public class DataTestQueryController {

    private final CurrentUserApiKeyOptionService currentUserApiKeyOptionService;
    private final ApiKeyInterfaceService apiKeyInterfaceService;
    private final ApiKeyProductService apiKeyProductService;
    private final CallerProductService callerProductService;
    private final ApiInterfaceFeignClient apiInterfaceFeignClient;
    private final VendorConfigInternalFeignClient vendorConfigInternalFeignClient;
    private final CallSceneService callSceneService;
    private final OpenApiQueryController openApiQueryController;

    public DataTestQueryController(
            CurrentUserApiKeyOptionService currentUserApiKeyOptionService,
            ApiKeyInterfaceService apiKeyInterfaceService,
            ApiKeyProductService apiKeyProductService,
            CallerProductService callerProductService,
            ApiInterfaceFeignClient apiInterfaceFeignClient,
            VendorConfigInternalFeignClient vendorConfigInternalFeignClient,
            CallSceneService callSceneService,
            OpenApiQueryController openApiQueryController) {
        this.currentUserApiKeyOptionService = currentUserApiKeyOptionService;
        this.apiKeyInterfaceService = apiKeyInterfaceService;
        this.apiKeyProductService = apiKeyProductService;
        this.callerProductService = callerProductService;
        this.apiInterfaceFeignClient = apiInterfaceFeignClient;
        this.vendorConfigInternalFeignClient = vendorConfigInternalFeignClient;
        this.callSceneService = callSceneService;
        this.openApiQueryController = openApiQueryController;
    }

    @GetMapping("/options")
    public ResponseEntity<Result<DataTestOptionsVO>> options(
            @RequestParam(required = false) Long apiKeyId) {
        if (!hasDataTestCapability()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(HttpStatus.FORBIDDEN.value(), "没有数据测试权限"));
        }
        try {
            Set<Long> authorizedInterfaceIds = resolveAuthorizedInterfaceIds(
                    UserContext.getCurrentUserId(), UserContext.getCurrentTenantId(), apiKeyId);
            if (authorizedInterfaceIds == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Result.error(HttpStatus.FORBIDDEN.value(), "无权使用该API Key"));
            }

            Result<List<VendorConfigDTO>> configResult = vendorConfigInternalFeignClient.list(
                    null, null, null, "active");
            Result<List<ApiInterfaceDTO>> interfaceResult = apiInterfaceFeignClient.getOptions(null);
            if (!isSuccessful(configResult) || !isSuccessful(interfaceResult)) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Result.error(HttpStatus.BAD_GATEWAY.value(), "数据测试选项服务暂不可用"));
            }

            Map<Long, ApiInterfaceDTO> interfacesById = new LinkedHashMap<>();
            for (ApiInterfaceDTO apiInterface : interfaceResult.getData()) {
                if (apiInterface != null && apiInterface.getId() != null
                        && "active".equalsIgnoreCase(apiInterface.getStatus())) {
                    interfacesById.put(apiInterface.getId(), apiInterface);
                }
            }

            Map<Long, DataTestOptionsVO.VendorOption> vendors = new LinkedHashMap<>();
            Map<Long, DataTestOptionsVO.DataTypeOption> dataTypes = new LinkedHashMap<>();
            Set<String> interfaceKeys = new LinkedHashSet<>();
            List<DataTestOptionsVO.InterfaceOption> interfaceOptions = new ArrayList<>();
            for (VendorConfigDTO config : configResult.getData()) {
                if (config == null || config.getId() == null
                        || config.getVendorId() == null || config.getDataTypeId() == null
                        || config.getInterfaceId() == null
                        || !authorizedInterfaceIds.contains(config.getInterfaceId())) {
                    continue;
                }

                ApiInterfaceDTO apiInterface = interfacesById.get(config.getInterfaceId());
                if (apiInterface == null) {
                    continue;
                }
                vendors.putIfAbsent(config.getVendorId(), new DataTestOptionsVO.VendorOption(
                        config.getVendorId(), config.getVendorName()));
                dataTypes.putIfAbsent(config.getDataTypeId(), new DataTestOptionsVO.DataTypeOption(
                        config.getDataTypeId(), config.getDataTypeCode(), config.getDataTypeName()));

                String key = config.getInterfaceId() + ":" + config.getVendorId()
                        + ":" + config.getDataTypeId();
                if (interfaceKeys.add(key)) {
                    interfaceOptions.add(new DataTestOptionsVO.InterfaceOption(
                            apiInterface.getId(),
                            apiInterface.getInterfaceCode(),
                            apiInterface.getInterfaceName(),
                            config.getVendorId(),
                            config.getDataTypeId()));
                }
            }

            List<DataTestOptionsVO.SceneOption> scenes = callSceneService.list().stream()
                    .filter(scene -> scene != null
                            && !Boolean.TRUE.equals(scene.getDeleted())
                            && "active".equalsIgnoreCase(scene.getStatus()))
                    .map(this::toSceneOption)
                    .sorted(Comparator.comparing(DataTestOptionsVO.SceneOption::sceneCode))
                    .toList();
            List<DataTestOptionsVO.ProductOption> products = resolveAuthorizedProducts(
                    UserContext.getCurrentUserId(), UserContext.getCurrentTenantId(), apiKeyId);

            return ResponseEntity.ok(Result.success(new DataTestOptionsVO(
                    List.copyOf(vendors.values()),
                    List.copyOf(dataTypes.values()),
                    List.copyOf(interfaceOptions),
                    scenes,
                    products)));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Result.error(HttpStatus.BAD_GATEWAY.value(), "数据测试选项服务暂不可用"));
        }
    }

    private List<DataTestOptionsVO.ProductOption> resolveAuthorizedProducts(
            Long userId,
            Long tenantId,
            Long apiKeyId) {
        if (apiKeyId == null) {
            return List.of();
        }
        ApiKey apiKey = currentUserApiKeyOptionService.findUsableKey(userId, tenantId, apiKeyId);
        if (apiKey == null || apiKey.getCallerId() == null) {
            return List.of();
        }
        List<Long> grantedProductIds = apiKeyProductService.getProductIdsByApiKeyId(apiKeyId);
        if (grantedProductIds == null) {
            throw new IllegalStateException("API Key产品授权数据异常");
        }
        Set<Long> granted = grantedProductIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<CallerProduct> products = callerProductService.listByCaller(apiKey.getCallerId());
        if (products == null) {
            throw new IllegalStateException("调用方产品数据异常");
        }
        return products.stream()
                .filter(java.util.Objects::nonNull)
                .filter(product -> product.getId() != null && granted.contains(product.getId()))
                .filter(product -> java.util.Objects.equals(apiKey.getCallerId(), product.getCallerId()))
                .filter(product -> !Boolean.TRUE.equals(product.getDeleted()))
                .filter(product -> StatusConstants.ACTIVE.equalsIgnoreCase(product.getStatus()))
                .map(product -> new DataTestOptionsVO.ProductOption(
                        product.getId(), product.getProductCode(), product.getProductName(), product.getStatus()))
                .toList();
    }

    /**
     * Returns only keys that the current user may actually use. A null result
     * means that an explicitly selected key is not usable and must be rejected.
     */
    private Set<Long> resolveAuthorizedInterfaceIds(Long userId, Long tenantId, Long apiKeyId) {
        Set<Long> authorizedApiKeyIds;
        if (apiKeyId != null) {
            if (currentUserApiKeyOptionService.findUsableKey(userId, tenantId, apiKeyId) == null) {
                return null;
            }
            authorizedApiKeyIds = Set.of(apiKeyId);
        } else {
            CurrentUserApiKeyOptionsVO options = currentUserApiKeyOptionService.listOptions(userId, tenantId);
            if (options == null || options.getOptions() == null) {
                return Set.of();
            }
            authorizedApiKeyIds = options.getOptions().stream()
                    .map(CurrentUserApiKeyOptionVO::getId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        if (authorizedApiKeyIds.isEmpty()) {
            return Set.of();
        }
        return authorizedApiKeyIds.stream()
                .flatMap(keyId -> {
                    List<com.dataplatform.access.caller.entity.ApiKeyInterface> grants =
                            apiKeyInterfaceService.listEffectiveGrants(keyId);
                    return grants == null ? java.util.stream.Stream.empty() : grants.stream();
                })
                .filter(java.util.Objects::nonNull)
                .map(com.dataplatform.access.caller.entity.ApiKeyInterface::getInterfaceId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @GetMapping("/contract")
    public ResponseEntity<Result<InterfaceContractDTO>> contract(
            @RequestParam Long apiKeyId,
            @RequestParam Long interfaceId) {
        if (!hasDataTestCapability()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(HttpStatus.FORBIDDEN.value(), "没有数据测试权限"));
        }
        return contractForUser(
                UserContext.getCurrentUserId(),
                UserContext.getCurrentTenantId(),
                apiKeyId,
                interfaceId);
    }

    ResponseEntity<Result<InterfaceContractDTO>> contractForUser(
            Long userId,
            Long tenantId,
            Long apiKeyId,
            Long interfaceId) {
        if (apiKeyId == null || interfaceId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(HttpStatus.BAD_REQUEST.value(), "apiKeyId和interfaceId不能为空"));
        }

        try {
            ApiKey apiKey = currentUserApiKeyOptionService.findUsableKey(userId, tenantId, apiKeyId);
            if (apiKey == null || apiKeyInterfaceService.findEffectiveGrant(apiKeyId, interfaceId) == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Result.error(HttpStatus.FORBIDDEN.value(), "无权读取该接口契约"));
            }

            Result<ApiInterfaceDTO> apiInterface = apiInterfaceFeignClient.getById(interfaceId);
            if (apiInterface == null || apiInterface.getCode() == null
                    || apiInterface.getCode() >= 500) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Result.error(HttpStatus.BAD_GATEWAY.value(), "接口服务暂不可用"));
            }
            if (!isSuccessful(apiInterface)
                    || !"active".equalsIgnoreCase(apiInterface.getData().getStatus())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.error(HttpStatus.NOT_FOUND.value(), "接口不存在或未启用"));
            }
            Result<InterfaceContractDTO> contract = apiInterfaceFeignClient.getContract(interfaceId);
            if (contract == null || contract.getCode() == null || contract.getCode() >= 500) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Result.error(HttpStatus.BAD_GATEWAY.value(), "接口契约服务暂不可用"));
            }
            if (!Integer.valueOf(HttpStatus.OK.value()).equals(contract.getCode())
                    || contract.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.error(HttpStatus.NOT_FOUND.value(), "接口契约不存在"));
            }
            return ResponseEntity.ok(Result.success(contract.getData()));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Result.error(HttpStatus.BAD_GATEWAY.value(), "接口契约服务暂不可用"));
        }
    }

    @PostMapping("/query")
    public ResponseEntity<Result<OpenApiQueryRespVO>> query(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody DataTestQueryReqVO request,
            HttpServletRequest httpRequest) {
        if (!hasDataTestCapability()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(HttpStatus.FORBIDDEN.value(), "没有数据测试权限"));
        }
        return queryForUser(
                UserContext.getCurrentUserId(),
                UserContext.getCurrentTenantId(),
                traceId,
                request,
                httpRequest);
    }

    ResponseEntity<Result<OpenApiQueryRespVO>> queryForUser(
            Long userId,
            Long tenantId,
            String traceId,
            DataTestQueryReqVO request,
            HttpServletRequest httpRequest) {
        if (request == null || request.getApiKeyId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(HttpStatus.BAD_REQUEST.value(), "apiKeyId不能为空"));
        }

        try {
            ApiKey apiKey = currentUserApiKeyOptionService.findUsableKey(
                    userId,
                    tenantId,
                    request.getApiKeyId());
            if (apiKey == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Result.error(HttpStatus.FORBIDDEN.value(), "无权使用该API Key"));
            }

            return openApiQueryController.query(
                    apiKey.getApiKey(), null, traceId, request, httpRequest);
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Result.error(HttpStatus.BAD_GATEWAY.value(), "数据测试查询服务暂不可用"));
        }
    }

    private boolean isSuccessful(Result<?> result) {
        return result != null
                && Integer.valueOf(HttpStatus.OK.value()).equals(result.getCode())
                && result.getData() != null;
    }

    private DataTestOptionsVO.SceneOption toSceneOption(CallScene scene) {
        return new DataTestOptionsVO.SceneOption(
                scene.getId(), scene.getSceneCode(), scene.getSceneName(), scene.getStatus());
    }

    private boolean hasDataTestCapability() {
        return UserContext.hasPermission("system:admin")
                || UserContext.hasPermission("api-permission:view")
                || UserContext.hasPermission("api-permission:apply");
    }
}
