package com.dataplatform.masterdata.vendor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.masterdata.vendor.entity.ConfigVersion;
import com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig;

import java.util.List;

public interface VendorExtendedConfigService extends IService<VendorExtendedConfig> {

    PageResult<VendorExtendedConfig> list(Long vendorId, String keyword, int page, int pageSize);

    List<VendorExtendedConfig> getByVendor(Long vendorId);

    String getConfig(String configKey);

    boolean updateConfig(String configKey, String configValue, Long updatedBy);

    boolean publishConfig(String configKey);

    boolean rollback(String configKey, Long versionId);

    List<ConfigVersion> getVersionHistory(String configKey);

    void clearAllCache();
}
