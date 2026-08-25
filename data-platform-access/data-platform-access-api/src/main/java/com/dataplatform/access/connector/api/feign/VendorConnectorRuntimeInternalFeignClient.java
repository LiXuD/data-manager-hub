package com.dataplatform.access.connector.api.feign;

import com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "data-platform-access", contextId = "accessVendorConnectorRuntimeInternalClient",
        path = "/internal/v1/access/vendor-connectors")
@InternalFeignContract
public interface VendorConnectorRuntimeInternalFeignClient {

    @PostMapping("/test")
    Result<VendorConnectorTestRespDTO> test(@RequestBody VendorConnectorTestReqDTO request);
}
