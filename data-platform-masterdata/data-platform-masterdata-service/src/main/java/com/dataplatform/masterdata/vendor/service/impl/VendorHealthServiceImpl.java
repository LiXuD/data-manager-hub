package com.dataplatform.masterdata.vendor.service.impl;

import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorMapper;
import com.dataplatform.masterdata.vendor.service.VendorHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 主数据域厂商的 Vendor Health Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
@Service
public class VendorHealthServiceImpl implements VendorHealthService {

    private static final Logger log = LoggerFactory.getLogger(VendorHealthServiceImpl.class);

    private static final String HEALTH_CACHE_PREFIX = "vendor:health:";
    private static final String FAILURE_COUNT_PREFIX = "vendor:failures:";
    private static final int HEALTH_CHECK_INTERVAL_SECONDS = 30;
    private static final int FAILURE_THRESHOLD = 3;
    private static final int HEALTH_CACHE_TTL_SECONDS = 60;

    @Autowired
    private VendorConfigMapper vendorConfigMapper;

    @Autowired
    private VendorMapper vendorMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean checkHealth(Long vendorId) {
        try {
            // 获取厂商配置
            VendorConfig config = vendorConfigMapper.selectById(vendorId);
            if (config == null || config.getApiUrl() == null) {
                return false;
            }

            // 执行健康检查 (简单的GET请求测试连通性)
            // 实际应该调用配置的healthCheckUrl
            String healthCheckUrl = config.getApiUrl();
            // 如果有专门的健康检查URL则使用
            // healthCheckUrl = vendorConfig.getHealthCheckUrl();

            long startTime = System.currentTimeMillis();
            restTemplate.headForHeaders(healthCheckUrl);
            long latency = System.currentTimeMillis() - startTime;

            // 响应时间<5秒认为健康
            boolean healthy = latency < 5000;

            if (healthy) {
                // 健康：清除失败计数
                redisTemplate.delete(FAILURE_COUNT_PREFIX + vendorId);
                markHealthy(vendorId);
            }

            return healthy;
        } catch (Exception e) {
            log.warn("厂商健康检查失败: vendorId={}, error={}", vendorId, e.getMessage());
            markUnhealthy(vendorId);
            return false;
        }
    }

    @Override
    public Map<Long, Boolean> checkBatchHealth(List<Long> vendorIds) {
        Map<Long, Boolean> results = new HashMap<>();
        for (Long vendorId : vendorIds) {
            results.put(vendorId, checkHealth(vendorId));
        }
        return results;
    }

    @Override
    public Long getAvailableVendor(Long vendorId) {
        // 1. 先检查主厂商是否健康
        if (isHealthy(vendorId)) {
            return vendorId;
        }

        // 2. 主厂商不健康，检查是否有备用厂商
        VendorConfig config = vendorConfigMapper.selectById(vendorId);
        if (config != null && config.getFallbackVendorId() != null) {
            Long fallbackId = config.getFallbackVendorId();
            // 检查备用厂商是否健康
            if (isHealthy(fallbackId)) {
                log.info("主厂商{}不可用，切换到备用厂商{}", vendorId, fallbackId);
                return fallbackId;
            }
        }

        // 3. 都没有可用的，返回主厂商(让调用方处理失败)
        return vendorId;
    }

    @Override
    public void markUnhealthy(Long vendorId) {
        // 增加失败计数
        Long count = redisTemplate.opsForValue().increment(FAILURE_COUNT_PREFIX + vendorId);
        if (count != null && count >= FAILURE_THRESHOLD) {
            // 连续失败达到阈值，标记为不健康
            redisTemplate.opsForValue().set(
                HEALTH_CACHE_PREFIX + vendorId,
                "false",
                HEALTH_CACHE_TTL_SECONDS,
                TimeUnit.SECONDS
            );
            log.warn("厂商{}连续失败{}次，标记为不健康", vendorId, count);
        }
    }

    @Override
    public void markHealthy(Long vendorId) {
        // 清除失败计数
        redisTemplate.delete(FAILURE_COUNT_PREFIX + vendorId);
        // 标记为健康
        redisTemplate.opsForValue().set(
            HEALTH_CACHE_PREFIX + vendorId,
            "true",
            HEALTH_CACHE_TTL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    @Override
    public boolean isHealthy(Long vendorId) {
        try {
            String status = redisTemplate.opsForValue().get(HEALTH_CACHE_PREFIX + vendorId);
            if ("true".equals(status)) {
                return true;
            }
            if ("false".equals(status)) {
                return false;
            }
            // 缓存不存在，执行健康检查
            return checkHealth(vendorId);
        } catch (Exception e) {
            log.warn("获取厂商健康状态失败: vendorId={}", vendorId);
            // 异常时默认健康，让请求尝试
            return true;
        }
    }
}