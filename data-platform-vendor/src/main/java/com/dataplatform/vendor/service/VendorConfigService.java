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
}
