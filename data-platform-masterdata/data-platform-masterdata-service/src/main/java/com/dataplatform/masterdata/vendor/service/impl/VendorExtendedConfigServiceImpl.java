package com.dataplatform.masterdata.vendor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.masterdata.vendor.entity.ConfigVersion;
import com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig;
import com.dataplatform.masterdata.vendor.mapper.ConfigVersionMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorExtendedConfigMapper;
import com.dataplatform.masterdata.vendor.service.VendorExtendedConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 主数据域厂商的 Vendor Extended Config Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
@Service
public class VendorExtendedConfigServiceImpl extends ServiceImpl<VendorExtendedConfigMapper, VendorExtendedConfig> implements VendorExtendedConfigService {

    private static final Logger log = LoggerFactory.getLogger(VendorExtendedConfigServiceImpl.class);
    private static final String CONFIG_CACHE_PREFIX = "config:";
    private static final int CONFIG_CACHE_TTL_SECONDS = 3600;

    @Autowired
    private ConfigVersionMapper configVersionMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public PageResult<VendorExtendedConfig> list(Long vendorId, String keyword, int page, int pageSize) {
        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        if (vendorId != null) {
            wrapper.eq(VendorExtendedConfig::getVendorId, vendorId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(VendorExtendedConfig::getConfigKey, keyword);
        }
        wrapper.orderByDesc(VendorExtendedConfig::getUpdatedAt);

        Page<VendorExtendedConfig> result = this.page(new Page<>(page, pageSize), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    public List<VendorExtendedConfig> getByVendor(Long vendorId) {
        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorExtendedConfig::getVendorId, vendorId);
        wrapper.eq(VendorExtendedConfig::getIsActive, true);
        return list(wrapper);
    }

    @Override
    public String getConfig(String configKey) {
        String cacheKey = CONFIG_CACHE_PREFIX + configKey;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            log.debug("配置命中缓存: {}", configKey);
            return cachedValue;
        }

        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorExtendedConfig::getConfigKey, configKey);
        wrapper.eq(VendorExtendedConfig::getIsActive, true);
        VendorExtendedConfig config = this.getOne(wrapper);

        if (config != null && config.getConfigValue() != null) {
            redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(),
                CONFIG_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            return config.getConfigValue();
        }

        return null;
    }

    @Override
    public boolean updateConfig(String configKey, String configValue, Long updatedBy) {
        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorExtendedConfig::getConfigKey, configKey);
        VendorExtendedConfig oldConfig = this.getOne(wrapper);

        if (oldConfig != null) {
            saveVersion(oldConfig);
            oldConfig.setConfigValue(configValue);
            oldConfig.setUpdatedBy(updatedBy);
            this.updateById(oldConfig);
        } else {
            VendorExtendedConfig newConfig = new VendorExtendedConfig();
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(configValue);
            newConfig.setIsActive(true);
            this.save(newConfig);
        }

        redisTemplate.delete(CONFIG_CACHE_PREFIX + configKey);
        log.info("配置已更新: {}, 缓存已清除", configKey);

        return true;
    }

    @Override
    public boolean publishConfig(String configKey) {
        String configValue = getConfig(configKey);
        if (configValue == null) {
            return false;
        }

        String notifyKey = CONFIG_CACHE_PREFIX + configKey + ":publish";
        redisTemplate.opsForValue().set(notifyKey, configValue, 60, TimeUnit.SECONDS);

        log.info("配置已发布: {}", configKey);
        return true;
    }

    @Override
    public boolean rollback(String configKey, Long versionId) {
        ConfigVersion version = configVersionMapper.selectById(versionId);
        if (version == null || !version.getConfigKey().equals(configKey)) {
            return false;
        }

        return updateConfig(configKey, version.getConfigValue(), null);
    }

    @Override
    public List<ConfigVersion> getVersionHistory(String configKey) {
        LambdaQueryWrapper<ConfigVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigVersion::getConfigKey, configKey);
        wrapper.orderByDesc(ConfigVersion::getCreatedAt);
        return configVersionMapper.selectList(wrapper);
    }

    @Override
    public void clearAllCache() {
        scanAndDelete(CONFIG_CACHE_PREFIX + "*");
    }

    private void saveVersion(VendorExtendedConfig config) {
        ConfigVersion version = new ConfigVersion();
        version.setConfigKey(config.getConfigKey());
        version.setConfigValue(config.getConfigValue());
        version.setVersionNum(getNextVersionNum(config.getConfigKey()));
        configVersionMapper.insert(version);
    }

    private Long getNextVersionNum(String configKey) {
        LambdaQueryWrapper<ConfigVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigVersion::getConfigKey, configKey);
        wrapper.orderByDesc(ConfigVersion::getVersionNum);
        wrapper.last("LIMIT 1");
        ConfigVersion latest = configVersionMapper.selectOne(wrapper);
        return latest != null ? latest.getVersionNum() + 1 : 1L;
    }

    private void scanAndDelete(String pattern) {
        var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
            .match(pattern)
            .count(100)
            .build();

        List<String> keysToDelete = new ArrayList<>();
        try (var cursor = redisTemplate.scan(scanOptions)) {
            cursor.forEachRemaining(key -> keysToDelete.add((String) key));
        } catch (Exception e) {
            log.error("SCAN 获取缓存key失败: {}", e.getMessage());
            return;
        }

        if (!keysToDelete.isEmpty()) {
            Long deleted = redisTemplate.delete(keysToDelete);
            log.info("批量删除 {} 个缓存", deleted);
        }
    }
}
