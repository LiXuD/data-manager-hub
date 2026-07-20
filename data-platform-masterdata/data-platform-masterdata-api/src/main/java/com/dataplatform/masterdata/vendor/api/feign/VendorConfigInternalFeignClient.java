package com.dataplatform.masterdata.vendor.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "data-platform-masterdata", contextId = "masterdataVendorConfigInternalClient",
        path = "/internal/v1/masterdata/vendor-configs")
@InternalFeignContract
public interface VendorConfigInternalFeignClient {

    @GetMapping
    Result<List<VendorConfigDTO>> list(
            @RequestParam(value = "vendorId", required = false) Long vendorId,
            @RequestParam(value = "dataTypeId", required = false) Long dataTypeId,
            @RequestParam(value = "interfaceId", required = false) Long interfaceId,
            @RequestParam(value = "status", required = false) String status);

    @GetMapping("/{id}")
    Result<VendorConfigDTO> getById(@PathVariable("id") Long id);

    @GetMapping("/by-code")
    Result<VendorConfigDTO> getByVendorCodeAndDataTypeCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataTypeCode") String dataTypeCode);

    @GetMapping("/by-interface-code")
    Result<VendorConfigDTO> getByVendorCodeAndInterfaceCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("interfaceCode") String interfaceCode);

    @GetMapping("/by-vendor-and-data-type")
    Result<VendorConfigDTO> getByVendorIdAndDataTypeCode(
            @RequestParam("vendorId") Long vendorId,
            @RequestParam("dataTypeCode") String dataTypeCode);

    @GetMapping("/secret-key")
    Result<String> getSecretKey(@RequestParam("vendorCode") String vendorCode);
}
