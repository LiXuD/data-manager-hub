package com.dataplatform.identity.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.identity.api.dto.EncryptionReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "data-platform-identity", contextId = "identityEncryptionInternalClient",
        path = "/internal/v1/identity/encryption")
@InternalFeignContract
public interface EncryptionInternalFeignClient {

    @PostMapping("/encrypt")
    Result<String> encrypt(@RequestBody EncryptionReqDTO request);

    @PostMapping("/decrypt")
    Result<String> decrypt(@RequestBody EncryptionReqDTO request);
}
