package com.dataplatform.vendor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.vendor.entity.ConfigVersion;
import com.dataplatform.vendor.entity.VendorConfigExtended;

import java.util.List;

/**
 * 厂商扩展配置服务（配置中心）
 */
public interface VendorConfigExtendedService extends IService<VendorConfigExtended> {

    /**
     * 分页查询配置
     */
    PageResult<VendorConfigExtended> list(Long vendorId, String keyword, int page, int pageSize);

    /**
     * 获取供应商的激活配置
     */
    List<VendorConfigExtended> getByVendor(Long vendorId);

    /**
     * 获取配置（带缓存）
     */
    String getConfig(String configKey);

    /**
     * 更新配置（带版本管理和缓存清除）
     */
    boolean updateConfig(String configKey, String configValue, Long updatedBy);

    /**
     * 发布配置
     */
    boolean publishConfig(String configKey);

    /**
     * 回滚配置到指定版本
     */
    boolean rollback(String configKey, Long versionId);

    /**
     * 获取配置版本历史
     */
    List<ConfigVersion> getVersionHistory(String configKey);

    /**
     * 清除所有配置缓存
     */
    void clearAllCache();
}
