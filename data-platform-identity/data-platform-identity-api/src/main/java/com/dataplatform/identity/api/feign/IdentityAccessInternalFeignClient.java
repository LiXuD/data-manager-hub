package com.dataplatform.identity.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.identity.api.dto.CallerAccessDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 身份域用户数据范围与角色的内部查询契约。
 */
@FeignClient(name = "data-platform-identity", contextId = "identityAccessClient",
        path = "/internal/v1/identity/users")
@InternalFeignContract
public interface IdentityAccessInternalFeignClient {

    @GetMapping("/{userId}/callers/{callerId}/access")
    Result<CallerAccessDTO> getCallerAccess(
            @PathVariable("userId") Long userId,
            @PathVariable("callerId") Long callerId);

    @GetMapping("/{userId}/callers")
    Result<List<Long>> getCallerIds(@PathVariable("userId") Long userId);

    @GetMapping("/{userId}/roles")
    Result<List<String>> getRoleCodes(@PathVariable("userId") Long userId);
}
