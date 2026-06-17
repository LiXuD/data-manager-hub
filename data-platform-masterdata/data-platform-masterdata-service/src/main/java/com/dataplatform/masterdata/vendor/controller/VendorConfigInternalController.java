package com.dataplatform.masterdata.vendor.controller;

import com.dataplatform.api.Result;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 主数据域厂商的 Vendor Config Internal Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/vendor/config/internal")
public class VendorConfigInternalController {

    @Autowired
    private VendorConfigService vendorConfigService;

    @GetMapping("/byVendorIdAndDataTypeCode")
    public Result<VendorConfigDTO> getByVendorIdAndDataTypeCode(
            @RequestParam("vendorId") Long vendorId,
            @RequestParam("dataTypeCode") String dataTypeCode) {
        VendorConfig config = vendorConfigService.getByVendorIdAndDataTypeCode(vendorId, dataTypeCode);
        if (config == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(config));
    }

    @GetMapping("/byInterfaceCode")
    public Result<VendorConfigDTO> getByVendorCodeAndInterfaceCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("interfaceCode") String interfaceCode) {
        VendorConfig config = vendorConfigService.getByVendorCodeAndInterfaceCode(vendorCode, interfaceCode);
        if (config == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(config));
    }

    @GetMapping("/byCode")
    public Result<VendorConfigDTO> getByVendorCodeAndDataTypeCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataTypeCode") String dataTypeCode) {
        VendorConfig config = vendorConfigService.getByVendorCodeAndDataTypeCode(vendorCode, dataTypeCode);
        if (config == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(config));
    }

    @GetMapping("/secretKey")
    public Result<String> getSecretKey(@RequestParam("vendorCode") String vendorCode) {
        String secretKey = vendorConfigService.getSecretKey(vendorCode);
        return Result.success(secretKey);
    }

    @GetMapping("/{id}")
    public Result<VendorConfigDTO> getById(@PathVariable("id") Long id) {
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(config));
    }

    private VendorConfigDTO toDTO(VendorConfig entity) {
        VendorConfigDTO dto = new VendorConfigDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
