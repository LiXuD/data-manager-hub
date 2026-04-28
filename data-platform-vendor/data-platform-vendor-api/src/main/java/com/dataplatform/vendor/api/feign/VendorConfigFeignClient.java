package com.dataplatform.vendor.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.vendor.api.dto.VendorConfigCreateReqDTO;
import com.dataplatform.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.vendor.api.dto.VendorConfigUpdateReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 厂商配置服务Feign客户端
 */
@FeignClient(name = "data-platform-vendor", contextId = "vendorConfigFeignClient")
public interface VendorConfigFeignClient {

    /**
     * 获取所有配置列表
     */
    @GetMapping("/vendor/config/list")
    Result<List<VendorConfigDTO>> list();

    /**
     * 根据ID获取配置
     */
    @GetMapping("/vendor/config/{id}")
    Result<VendorConfigDTO> getById(@PathVariable("id") Long id);

    /**
     * 根据厂商ID获取配置列表
     */
    @GetMapping("/vendor/config/vendor/{vendorId}")
    Result<List<VendorConfigDTO>> listByVendorId(@PathVariable("vendorId") Long vendorId);

    /**
     * 根据厂商编码和数据类型编码获取配置
     */
    @GetMapping("/vendor/config/byCode")
    Result<VendorConfigDTO> getByVendorCodeAndDataTypeCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataTypeCode") String dataTypeCode);

    /**
     * 创建配置
     */
    @PostMapping("/vendor/config")
    Result<VendorConfigDTO> create(@RequestBody VendorConfigCreateReqDTO dto);

    /**
     * 更新配置
     */
    @PutMapping("/vendor/config/{id}")
    Result<VendorConfigDTO> update(@PathVariable("id") Long id, @RequestBody VendorConfigUpdateReqDTO dto);

    /**
     * 删除配置
     */
    @DeleteMapping("/vendor/config/{id}")
    Result<Void> delete(@PathVariable("id") Long id);

    /**
     * 获取厂商密钥
     */
    @GetMapping("/vendor/config/secretKey")
    Result<String> getSecretKey(@RequestParam("vendorCode") String vendorCode);
}
