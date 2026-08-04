package com.dataplatform.masterdata.connector.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRuntimeSnapshotDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "data-platform-masterdata", contextId = "masterdataVendorConnectorInternalClient",
        path = "/internal/v1/masterdata/vendor-configs")
@InternalFeignContract
public interface VendorConnectorInternalFeignClient {

    @GetMapping("/{vendorConfigId}/connector-runtime")
    Result<VendorConnectorRuntimeSnapshotDTO> getRuntimeSnapshot(
            @PathVariable("vendorConfigId") Long vendorConfigId);
}
