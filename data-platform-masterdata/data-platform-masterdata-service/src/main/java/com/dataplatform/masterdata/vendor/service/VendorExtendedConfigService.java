package com.dataplatform.masterdata.vendor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.masterdata.vendor.entity.ConfigVersion;
import com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig;

import java.util.List;

/**
 * 主数据域厂商的 Vendor Extended Config Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
public interface VendorExtendedConfigService extends IService<VendorExtendedConfig> {

    PageResult<VendorExtendedConfig> list(Long vendorId, String keyword, int page, int pageSize);

    List<VendorExtendedConfig> getByVendor(Long vendorId);

    VendorExtendedConfig getForDisplay(Long id);

    VendorExtendedConfig saveSecure(VendorExtendedConfig config);

    VendorExtendedConfig updateSecure(Long id, VendorExtendedConfig config);

    boolean removeSecure(Long id);

    boolean updateStatusSecure(Long id, String status);

    String getConfig(String configKey);

    String getConfig(Long vendorId, String configKey);

    String getDisplayValue(String configKey);

    boolean updateConfig(String configKey, String configValue, Long updatedBy);

    boolean publishConfig(String configKey);

    boolean rollback(String configKey, Long versionId);

    List<ConfigVersion> getVersionHistory(String configKey);

    void clearAllCache();
}
