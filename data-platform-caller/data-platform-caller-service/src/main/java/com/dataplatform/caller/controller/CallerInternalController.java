package com.dataplatform.caller.controller;

import com.dataplatform.api.Result;
import com.dataplatform.caller.api.dto.ApiKeyDTO;
import com.dataplatform.caller.entity.ApiKey;
import com.dataplatform.caller.service.ApiKeyService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/caller/internal")
public class CallerInternalController {

    @Autowired
    private ApiKeyService apiKeyService;

    @GetMapping("/apiKey/validate/{apiKey}")
    public Result<ApiKeyDTO> validateApiKey(@PathVariable("apiKey") String apiKey) {
        ApiKey key = apiKeyService.getByKey(apiKey);
        if (key == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(key));
    }

    private ApiKeyDTO toDTO(ApiKey entity) {
        ApiKeyDTO dto = new ApiKeyDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
