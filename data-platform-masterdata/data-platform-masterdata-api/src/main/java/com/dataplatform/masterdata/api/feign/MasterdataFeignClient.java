package com.dataplatform.masterdata.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 主数据域远程调用的 Masterdata Feign Client。
 * <p>OpenFeign 远程调用契约，供其他服务依赖 api 模块完成跨域调用。</p>
 */
@FeignClient(name = "data-platform-masterdata", contextId = "masterdataFeignClient")
public interface MasterdataFeignClient {

    @GetMapping("/masterdata/vendor/code/{vendorCode}")
    Result<VendorInfoDTO> getVendorByCode(@PathVariable("vendorCode") String vendorCode);

    @GetMapping("/masterdata/vendor/config/by-interface-code")
    Result<VendorConfigDTO> getVendorConfigByInterfaceCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("interfaceCode") String interfaceCode);

    @GetMapping("/masterdata/interface/code/{interfaceCode}")
    Result<ApiInterfaceDTO> getInterfaceByCode(@PathVariable("interfaceCode") String interfaceCode);

    @GetMapping("/masterdata/interface/{id}")
    Result<ApiInterfaceDTO> getInterfaceById(@PathVariable("id") Long id);
}
