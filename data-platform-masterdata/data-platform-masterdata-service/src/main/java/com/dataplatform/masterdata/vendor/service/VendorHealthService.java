package com.dataplatform.masterdata.vendor.service;

import java.util.Map;

/**
 * 厂商健康检查服务
 * 负责检测厂商API可用性，自动切换到备用厂商
 */
public interface VendorHealthService {

    /**
     * 检测厂商健康状态
     * @param vendorId 厂商ID
     * @return true=健康, false=不健康
     */
    boolean checkHealth(Long vendorId);

    /**
     * 使用指定配置执行一次真实连接测试。
     */
    Map<String, Object> testConnection(Long configId);

    /**
     * 批量检测多个厂商
     * @param vendorIds 厂商ID列表
     * @return 厂商ID -> 健康状态
     */
    Map<Long, Boolean> checkBatchHealth(java.util.List<Long> vendorIds);

    /**
     * 获取健康但可用的厂商(主厂商不可用时返回备用)
     * @param vendorId 主厂商ID
     * @return 可用的厂商ID(如果主厂商健康则返回主厂商ID)
     */
    Long getAvailableVendor(Long vendorId);

    /**
     * 标记厂商为不健康(连续失败后调用)
     */
    void markUnhealthy(Long vendorId);

    /**
     * 标记厂商为健康(恢复检测成功后调用)
     */
    void markHealthy(Long vendorId);

    /**
     * 获取厂商当前健康状态(从缓存)
     */
    boolean isHealthy(Long vendorId);
}
