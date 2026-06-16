package com.dataplatform.access.caller.controller;

import com.dataplatform.api.Result;
import com.dataplatform.access.caller.api.dto.ApiKeyDTO;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/caller/internal")
public class CallerInternalController {

    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ApiKeyInterfaceService apiKeyInterfaceService;

    @GetMapping("/apiKey/validate/{apiKey}")
    public Result<ApiKeyDTO> validateApiKey(@PathVariable("apiKey") String apiKey) {
        ApiKey key = apiKeyService.getByKey(apiKey);
        if (key == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(key));
    }

    @GetMapping("/apiKey/{apiKeyId}/hasInterfacePermission/{interfaceId}")
    public Result<Boolean> hasInterfacePermission(
            @PathVariable("apiKeyId") Long apiKeyId,
            @PathVariable("interfaceId") Long interfaceId) {
        boolean hasPermission = apiKeyInterfaceService.hasInterfacePermission(apiKeyId, interfaceId);
        return Result.success(hasPermission);
    }

    private ApiKeyDTO toDTO(ApiKey entity) {
        ApiKeyDTO dto = new ApiKeyDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
