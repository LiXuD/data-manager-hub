package com.dataplatform.caller.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.caller.api.dto.ApiKeyDTO;
import com.dataplatform.caller.api.dto.CallerInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "data-platform-caller-service", path = "/caller")
public interface CallerFeignClient {

    @GetMapping("/{id}")
    Result<CallerInfoDTO> getCaller(@PathVariable("id") Long id);

    @GetMapping("/apiKey/{apiKey}")
    Result<ApiKeyDTO> getApiKey(@PathVariable("apiKey") String apiKey);

    @GetMapping("/apiKey/validate/{apiKey}")
    Result<ApiKeyDTO> validateApiKey(@PathVariable("apiKey") String apiKey);

    @GetMapping("/internal/apiKey/{apiKeyId}/hasInterfacePermission/{interfaceId}")
    Result<Boolean> hasInterfacePermission(
            @PathVariable("apiKeyId") Long apiKeyId,
            @PathVariable("interfaceId") Long interfaceId);

    @PostMapping
    Result<Long> createCaller(@RequestBody CallerInfoDTO dto);

    @PutMapping("/{id}")
    Result<Void> updateCaller(@PathVariable("id") Long id, @RequestBody CallerInfoDTO dto);
}
