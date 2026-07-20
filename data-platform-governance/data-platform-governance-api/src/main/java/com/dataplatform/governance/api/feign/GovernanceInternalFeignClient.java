package com.dataplatform.governance.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.governance.api.dto.AlertRecordCreateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "data-platform-governance", contextId = "governanceInternalFeignClient",
        path = "/internal/v1/governance")
@InternalFeignContract
public interface GovernanceInternalFeignClient {

    @PostMapping("/alert-records")
    Result<Void> createAlertRecord(@RequestBody AlertRecordCreateDTO dto);
}
