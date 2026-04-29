package com.dataplatform.vendor.api.feign;

import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.vendor.api.dto.VendorCreateReqDTO;
import com.dataplatform.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.vendor.api.dto.VendorUpdateReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 厂商服务Feign客户端
 */
@FeignClient(name = "data-platform-vendor", contextId = "vendorFeignClient")
public interface VendorFeignClient {

    /**
     * 分页查询厂商列表
     */
    @GetMapping("/vendor/list")
    PageResult<VendorInfoDTO> list(
            @RequestParam("page") Integer page,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status);

    /**
     * 根据ID获取厂商
     */
    @GetMapping("/vendor/{id}")
    Result<VendorInfoDTO> getById(@PathVariable("id") Long id);

    /**
     * 根据厂商编码获取厂商
     */
    @GetMapping("/vendor/code/{vendorCode}")
    Result<VendorInfoDTO> getByVendorCode(@PathVariable("vendorCode") String vendorCode);

    /**
     * 创建厂商
     */
    @PostMapping("/vendor")
    Result<VendorInfoDTO> create(@RequestBody VendorCreateReqDTO dto);

    /**
     * 更新厂商
     */
    @PutMapping("/vendor/{id}")
    Result<VendorInfoDTO> update(@PathVariable("id") Long id, @RequestBody VendorUpdateReqDTO dto);

    /**
     * 删除厂商
     */
    @DeleteMapping("/vendor/{id}")
    Result<Void> delete(@PathVariable("id") Long id);

    /**
     * 更新厂商状态
     */
    @PatchMapping("/vendor/{id}/status")
    Result<Void> updateStatus(@PathVariable("id") Long id, @RequestParam("status") String status);
}
