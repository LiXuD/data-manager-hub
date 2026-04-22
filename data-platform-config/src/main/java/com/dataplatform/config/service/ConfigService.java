package com.dataplatform.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.config.entity.ConfigVersion;
import com.dataplatform.config.entity.VendorConfig;
import com.dataplatform.config.mapper.ConfigVersionMapper;
import com.dataplatform.config.mapper.VendorConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ConfigService extends ServiceImpl<VendorConfigMapper, VendorConfig> {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    private static final String CONFIG_CACHE_PREFIX = "config:";
    private static final int CONFIG_CACHE_TTL_SECONDS = 3600;

    @Autowired
    private ConfigVersionMapper configVersionMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public PageResult<VendorConfig> list(Long vendorId, String keyword, int page, int pageSize) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        if (vendorId != null) {
            wrapper.eq(VendorConfig::getVendorId, vendorId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(VendorConfig::getConfigKey, keyword);
        }
        wrapper.orderByDesc(VendorConfig::getUpdatedAt);

        Page<VendorConfig> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<VendorConfig> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    public List<VendorConfig> getByVendor(Long vendorId) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getVendorId, vendorId);
        wrapper.eq(VendorConfig::getIsActive, true);
        return list(wrapper);
    }

    /**
     * 获取配置 (带缓存)
     */
    public String getConfig(String configKey) {
        // 1. 尝试从缓存获取
        String cacheKey = CONFIG_CACHE_PREFIX + configKey;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            log.debug("配置命中缓存: {}", configKey);
            return cachedValue;
        }

        // 2. 缓存未命中，从数据库获取
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getConfigKey, configKey);
        wrapper.eq(VendorConfig::getIsActive, true);
        VendorConfig config = this.getOne(wrapper);

        if (config != null && config.getConfigValue() != null) {
            // 3. 存入缓存
            redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(),
                CONFIG_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            return config.getConfigValue();
        }

        return null;
    }

    /**
     * 更新配置 (带版本管理和缓存清除)
     */
    public boolean updateConfig(String configKey, String configValue, Long updatedBy) {
        // 1. 获取旧配置
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getConfigKey, configKey);
        VendorConfig oldConfig = this.getOne(wrapper);

        // 2. 保存版本历史
        if (oldConfig != null) {
            saveVersion(oldConfig);
        }

        // 3. 更新配置
        if (oldConfig != null) {
            oldConfig.setConfigValue(configValue);
            oldConfig.setUpdatedAt(LocalDateTime.now());
            oldConfig.setUpdatedBy(updatedBy);
            this.updateById(oldConfig);
        } else {
            // 新增配置
            VendorConfig newConfig = new VendorConfig();
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(configValue);
            newConfig.setIsActive(true);
            newConfig.setCreatedAt(LocalDateTime.now());
            newConfig.setUpdatedAt(LocalDateTime.now());
            this.save(newConfig);
        }

        // 4. 清除缓存
        redisTemplate.delete(CONFIG_CACHE_PREFIX + configKey);
        log.info("配置已更新: {}, 缓存已清除", configKey);

        return true;
    }

    /**
     * 发布配置 (推送到各服务)
     * 实际实现应该使用消息队列或配置中心推送
     */
    public boolean publishConfig(String configKey) {
        // 1. 获取最新配置
        String configValue = getConfig(configKey);
        if (configValue == null) {
            return false;
        }

        // 2. TODO: 推送到各服务
        // 可以使用Redis Pub/Sub或Kafka通知各服务刷新配置
        String notifyKey = CONFIG_CACHE_PREFIX + configKey + ":publish";
        redisTemplate.opsForValue().set(notifyKey, configValue, 60, TimeUnit.SECONDS);

        log.info("配置已发布: {}", configKey);
        return true;
    }

    /**
     * 回滚配置到指定版本
     */
    public boolean rollback(String configKey, Long versionId) {
        // 1. 获取历史版本
        ConfigVersion version = configVersionMapper.selectById(versionId);
        if (version == null || !version.getConfigKey().equals(configKey)) {
            return false;
        }

        // 2. 恢复到该版本的值
        return updateConfig(configKey, version.getConfigValue(), null);
    }

    /**
     * 获取配置版本历史
     */
    public List<ConfigVersion> getVersionHistory(String configKey) {
        LambdaQueryWrapper<ConfigVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigVersion::getConfigKey, configKey);
        wrapper.orderByDesc(ConfigVersion::getCreatedAt);
        return configVersionMapper.selectList(wrapper);
    }

    /**
     * 保存配置版本
     */
    private void saveVersion(VendorConfig config) {
        ConfigVersion version = new ConfigVersion();
        version.setConfigKey(config.getConfigKey());
        version.setConfigValue(config.getConfigValue());
        version.setVersionNum(getNextVersionNum(config.getConfigKey()));
        version.setCreatedAt(LocalDateTime.now());
        configVersionMapper.insert(version);
    }

    /**
     * 获取下一个版本号
     */
    private Long getNextVersionNum(String configKey) {
        LambdaQueryWrapper<ConfigVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigVersion::getConfigKey, configKey);
        wrapper.orderByDesc(ConfigVersion::getVersionNum);
        wrapper.last("LIMIT 1");
        ConfigVersion latest = configVersionMapper.selectOne(wrapper);
        return latest != null ? latest.getVersionNum() + 1 : 1L;
    }

    /**
     * 清除所有配置缓存
     */
    public void clearAllCache() {
        redisTemplate.delete(redisTemplate.keys(CONFIG_CACHE_PREFIX + "*"));
        log.info("已清除所有配置缓存");
    }
}