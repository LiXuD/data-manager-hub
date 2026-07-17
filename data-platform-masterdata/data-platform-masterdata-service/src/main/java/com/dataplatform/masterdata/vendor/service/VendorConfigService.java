package com.dataplatform.masterdata.vendor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;

import java.util.List;

/**
 * 主数据域厂商的 Vendor Config Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
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
     * 通过主数据域内 ApiInterfaceService 本地查询接口定义。
     */
    VendorConfig getByVendorCodeAndInterfaceCode(String vendorCode, String interfaceCode);

    /**
     * 根据厂商ID和数据类型编码获取配置
     */
    VendorConfig getByVendorIdAndDataTypeCode(Long vendorId, String dataTypeCode);

    /**
     * Resolve an active data type code for vendor configuration persistence.
     */
    Long getDataTypeIdByCode(String dataTypeCode);

    /**
     * 获取厂商密钥
     */
    String getSecretKey(String vendorCode);
}
