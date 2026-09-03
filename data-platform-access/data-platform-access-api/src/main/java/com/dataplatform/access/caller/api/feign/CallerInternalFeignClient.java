package com.dataplatform.access.caller.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 访问域调用方的内部校验契约。
 * <p>仅返回指定租户下仍可用的调用方 ID，不暴露调用方凭据或管理字段。</p>
 */
@FeignClient(name = "data-platform-access", contextId = "accessCallerInternalClient",
        path = "/internal/v1/access/callers")
@InternalFeignContract
public interface CallerInternalFeignClient {

    @PostMapping("/validate")
    Result<List<Long>> validate(
            @RequestParam("tenantId") Long tenantId,
            @RequestBody List<Long> callerIds);
}
