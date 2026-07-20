package com.dataplatform.access.docs;

import com.dataplatform.api.Result;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 调用方使用 API Key 查看其获授权接口文档。 */
@RestController
@RequestMapping("/openapi/v1/docs")
public class CallerOpenApiDocumentController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;
    private final ApiKeyInterfaceService apiKeyInterfaceService;
    private final ApiInterfaceFeignClient apiInterfaceFeignClient;
    private final OpenApiDocumentService documentService;
    private final OpenApiBaseUrlResolver baseUrlResolver;

    public CallerOpenApiDocumentController(ApiKeyService apiKeyService,
                                           ApiKeyInterfaceService apiKeyInterfaceService,
                                           ApiInterfaceFeignClient apiInterfaceFeignClient,
                                           OpenApiDocumentService documentService,
                                           OpenApiBaseUrlResolver baseUrlResolver) {
        this.apiKeyService = apiKeyService;
        this.apiKeyInterfaceService = apiKeyInterfaceService;
        this.apiInterfaceFeignClient = apiInterfaceFeignClient;
        this.documentService = documentService;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping("/interfaces")
    public Result<List<Map<String, Object>>> list(
            @RequestHeader(value = "X-Api-Key", required = false) String keyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ApiKey apiKey = authenticate(keyHeader, authorization);
        if (apiKey == null) {
            return Result.error(401, "无效的API Key");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long interfaceId : apiKeyInterfaceService.getInterfaceIdsByApiKeyId(apiKey.getId())) {
            Result<ApiInterfaceDTO> response = apiInterfaceFeignClient.getById(interfaceId);
            ApiInterfaceDTO item = response != null ? response.getData() : null;
            if (item == null || !"active".equalsIgnoreCase(item.getStatus())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("interfaceCode", item.getInterfaceCode());
            row.put("interfaceName", item.getInterfaceName());
            row.put("description", item.getDescription());
            result.add(row);
        }
        return Result.success(result);
    }

    @GetMapping("/interfaces/{apiCode}")
    public Result<Map<String, Object>> detail(
            @PathVariable("apiCode") String apiCode,
            @RequestHeader(value = "X-Api-Key", required = false) String keyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthorizedContract authorized = authorizedContract(apiCode, keyHeader, authorization);
        if (authorized.errorCode() != null) {
            return Result.error(authorized.errorCode(), authorized.errorMessage());
        }
        return Result.success(documentService.describe(authorized.contract(), baseUrlResolver.resolve()));
    }

    @GetMapping("/interfaces/{apiCode}/openapi")
    public ResponseEntity<String> download(
            @PathVariable("apiCode") String apiCode,
            @RequestParam(defaultValue = "json") String format,
            @RequestHeader(value = "X-Api-Key", required = false) String keyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthorizedContract authorized = authorizedContract(apiCode, keyHeader, authorization);
        if (authorized.errorCode() != null) {
            return ResponseEntity.status(authorized.errorCode()).body(authorized.errorMessage());
        }
        String content = documentService.serialize(documentService.buildDocument(
                authorized.contract(), baseUrlResolver.resolve()), format);
        MediaType mediaType = "yaml".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("application/yaml") : MediaType.APPLICATION_JSON;
        return ResponseEntity.ok().contentType(mediaType).body(content);
    }

    private AuthorizedContract authorizedContract(String apiCode, String keyHeader, String authorization) {
        ApiKey apiKey = authenticate(keyHeader, authorization);
        if (apiKey == null) {
            return new AuthorizedContract(null, 401, "无效的API Key");
        }
        Result<ApiInterfaceDTO> interfaceResult = apiInterfaceFeignClient.getByInterfaceCode(apiCode);
        ApiInterfaceDTO apiInterface = interfaceResult != null ? interfaceResult.getData() : null;
        if (apiInterface == null || !"active".equalsIgnoreCase(apiInterface.getStatus())) {
            return new AuthorizedContract(null, 404, "接口不存在");
        }
        if (!apiKeyInterfaceService.hasInterfacePermission(apiKey.getId(), apiInterface.getId())) {
            return new AuthorizedContract(null, 403, "API Key没有访问该接口的权限");
        }
        Result<InterfaceContractDTO> contractResult = apiInterfaceFeignClient.getContract(apiInterface.getId());
        InterfaceContractDTO contract = contractResult != null ? contractResult.getData() : null;
        if (contract == null) {
            return new AuthorizedContract(null, 404, "接口契约不存在");
        }
        return new AuthorizedContract(contract, null, null);
    }

    private ApiKey authenticate(String keyHeader, String authorization) {
        String raw = keyHeader;
        if ((raw == null || raw.isBlank()) && authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            raw = authorization.substring(BEARER_PREFIX.length());
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ApiKey apiKey = apiKeyService.getByKey(raw.trim());
        if (apiKey == null || apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            return null;
        }
        if (apiKey.getExpireTime() != null && apiKey.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        return apiKey;
    }

    private record AuthorizedContract(InterfaceContractDTO contract, Integer errorCode, String errorMessage) {
    }
}
