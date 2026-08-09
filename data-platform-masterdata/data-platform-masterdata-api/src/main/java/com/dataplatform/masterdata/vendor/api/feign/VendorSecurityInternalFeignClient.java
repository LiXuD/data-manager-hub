package com.dataplatform.masterdata.vendor.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.masterdata.vendor.api.dto.VendorRuntimeSecurityDTO;
import com.dataplatform.masterdata.vendor.api.dto.ConnectorSecretResolutionDTO;
import com.dataplatform.masterdata.vendor.api.dto.ConnectorSecretResolutionRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "data-platform-masterdata", contextId = "masterdataVendorSecurityInternalClient",
        path = "/internal/v1/masterdata/vendor-security")
@InternalFeignContract
public interface VendorSecurityInternalFeignClient {

    @GetMapping("/{configId}")
    Result<VendorRuntimeSecurityDTO> getRuntimeSecurity(@PathVariable("configId") Long configId);

    @PostMapping("/connector-secrets/resolve")
    Result<ConnectorSecretResolutionDTO> resolveConnectorSecrets(
            @RequestBody ConnectorSecretResolutionRequestDTO request);
}
