package com.dataplatform.access.caller.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.access.caller.api.dto.ApiKeyDTO;
import com.dataplatform.access.caller.api.dto.CallerInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 访问域调用方的 Caller Feign Client。
 * <p>OpenFeign 远程调用契约，供其他服务依赖 api 模块完成跨域调用。</p>
 */
@FeignClient(name = "data-platform-access", contextId = "accessCallerFeignClient", path = "/access/caller")
public interface CallerFeignClient {

    /**
     * 按调用方主键获取调用方基础信息。
     */
    @GetMapping("/{id}")
    Result<CallerInfoDTO> getCaller(@PathVariable("id") Long id);

    /**
     * 按 API Key 明文或摘要查询密钥配置。
     */
    @GetMapping("/apiKey/{apiKey}")
    Result<ApiKeyDTO> getApiKey(@PathVariable("apiKey") String apiKey);

    /**
     * 校验 API Key 是否存在、启用且仍满足授权约束。
     */
    @GetMapping("/apiKey/validate/{apiKey}")
    Result<ApiKeyDTO> validateApiKey(@PathVariable("apiKey") String apiKey);

    /**
     * 判断指定 API Key 是否具备访问目标接口的授权。
     */
    @GetMapping("/internal/apiKey/{apiKeyId}/hasInterfacePermission/{interfaceId}")
    Result<Boolean> hasInterfacePermission(
            @PathVariable("apiKeyId") Long apiKeyId,
            @PathVariable("interfaceId") Long interfaceId);

    /**
     * 创建调用方主记录，供身份域或管理端开通访问主体时调用。
     */
    @PostMapping
    Result<Long> createCaller(@RequestBody CallerInfoDTO dto);

    /**
     * 更新调用方基础资料。
     */
    @PutMapping("/{id}")
    Result<Void> updateCaller(@PathVariable("id") Long id, @RequestBody CallerInfoDTO dto);
}
