package com.dataplatform.masterdata.vendor.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "data-platform-masterdata", contextId = "masterdataVendorInternalClient",
        path = "/internal/v1/masterdata/vendors")
@InternalFeignContract
public interface VendorInternalFeignClient {

    @GetMapping("/{id}")
    Result<VendorInfoDTO> getById(@PathVariable("id") Long id);

    @GetMapping("/by-code/{vendorCode}")
    Result<VendorInfoDTO> getByVendorCode(@PathVariable("vendorCode") String vendorCode);
}
