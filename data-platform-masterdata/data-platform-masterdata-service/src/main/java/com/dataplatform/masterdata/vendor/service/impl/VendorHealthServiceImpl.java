package com.dataplatform.masterdata.vendor.service.impl;

import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.vendor.service.VendorAdapterConfigAssembler;
import com.dataplatform.masterdata.vendor.service.VendorHealthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.common.adapter.VendorAdapterFactory;
import com.dataplatform.common.enums.CommonStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
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
    private static final int FAILURE_THRESHOLD = 3;
    private static final int HEALTH_CACHE_TTL_SECONDS = 60;

    @Autowired
    private VendorConfigMapper vendorConfigMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private VendorInfoMapper vendorInfoMapper;

    @Autowired
    private VendorAdapterConfigAssembler adapterConfigAssembler;

    @Override
    public boolean checkHealth(Long vendorId) {
        VendorConfig config = findActiveConfig(vendorId);
        if (config == null) {
            return false;
        }
        boolean healthy = Boolean.TRUE.equals(testConfig(config).get("success"));
        updateCachedHealth(vendorId, healthy);
        return healthy;
    }

    @Override
    public Map<String, Object> testConnection(Long configId) {
        VendorConfig config = vendorConfigMapper.selectById(configId);
        if (config == null) {
            return Map.of("success", false, "message", "配置不存在");
        }
        Map<String, Object> result = testConfig(config);
        updateCachedHealth(config.getVendorId(), Boolean.TRUE.equals(result.get("success")));
        return result;
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
        VendorConfig config = findActiveConfig(vendorId);
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
            log.warn("获取厂商健康状态缓存失败，执行实时检查: vendorId={}", vendorId);
            return checkHealth(vendorId);
        }
    }

    private VendorConfig findActiveConfig(Long vendorId) {
        return vendorConfigMapper.selectOne(new LambdaQueryWrapper<VendorConfig>()
                .eq(VendorConfig::getVendorId, vendorId)
                .eq(VendorConfig::getStatus, CommonStatus.ACTIVE.getCode())
                .eq(VendorConfig::getDeleted, false)
                .orderByDesc(VendorConfig::getUpdatedAt)
                .last("LIMIT 1"));
    }

    private Map<String, Object> testConfig(VendorConfig config) {
        if (config.getApiUrl() == null || config.getApiUrl().isBlank()) {
            return Map.of("success", false, "message", "API地址未配置");
        }
        VendorInfo vendor = vendorInfoMapper.selectById(config.getVendorId());
        if (vendor == null || vendor.getVendorCode() == null || vendor.getVendorCode().isBlank()) {
            return Map.of("success", false, "message", "厂商不存在或编码未配置");
        }

        try {
            VendorAdapterConfig adapterConfig = adapterConfigAssembler.build(config, vendor);
            Map<String, Object> adapterResult = VendorAdapterFactory.getAdapter(vendor.getVendorCode())
                    .execute(adapterConfig, Map.of());
            Map<String, Object> result = new HashMap<>(adapterResult);
            boolean success = Boolean.TRUE.equals(adapterResult.get("success"));
            result.put("message", success ? "连接正常"
                    : String.valueOf(adapterResult.getOrDefault("errorMsg", "连接失败")));
            return result;
        } catch (Exception e) {
            return Map.of("success", false,
                    "message", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void updateCachedHealth(Long vendorId, boolean healthy) {
        if (vendorId == null) {
            return;
        }
        try {
            if (healthy) {
                markHealthy(vendorId);
            } else {
                markUnhealthy(vendorId);
            }
        } catch (Exception e) {
            log.warn("更新厂商健康缓存失败: vendorId={}, error={}", vendorId, e.getMessage());
        }
    }
}
