package com.dataplatform.vendor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.vendor.entity.VendorConfig;

import java.util.List;

public interface VendorConfigService extends IService<VendorConfig> {

    List<VendorConfig> listByVendor(Long vendorId);

    VendorConfig getByVendorAndDataType(Long vendorId, String dataType);

    /**
     * 根据厂商编码和数据类型编码获取配置
     */
    VendorConfig getByVendorCodeAndDataTypeCode(String vendorCode, String dataTypeCode);

    /**
     * 根据接口ID获取配置
     */
    VendorConfig getByInterfaceId(Long interfaceId);

    /**
     * 根据厂商编码和接口编码获取配置
     * TODO: 需要添加data-platform-interface依赖并注入ApiInterfaceService
     */
    VendorConfig getByVendorCodeAndInterfaceCode(String vendorCode, String interfaceCode);

    /**
     * 根据厂商ID和数据类型编码获取配置
     */
    VendorConfig getByVendorIdAndDataTypeCode(Long vendorId, String dataTypeCode);

    /**
     * 获取厂商密钥
     */
    String getSecretKey(String vendorCode);
}
