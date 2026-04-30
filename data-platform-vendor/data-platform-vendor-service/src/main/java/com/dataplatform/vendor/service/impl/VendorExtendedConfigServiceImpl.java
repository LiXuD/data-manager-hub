package com.dataplatform.vendor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.vendor.entity.ConfigVersion;
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorExtendedConfigServiceImpl.java
import com.dataplatform.vendor.entity.VendorExtendedConfig;
import com.dataplatform.vendor.mapper.ConfigVersionMapper;
import com.dataplatform.vendor.mapper.VendorExtendedConfigMapper;
import com.dataplatform.vendor.service.VendorExtendedConfigService;
========
import com.dataplatform.vendor.entity.VendorConfigExtended;
import com.dataplatform.vendor.mapper.ConfigVersionMapper;
import com.dataplatform.vendor.mapper.VendorConfigExtendedMapper;
import com.dataplatform.vendor.service.VendorConfigExtendedService;
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorConfigExtendedServiceImpl.java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorExtendedConfigServiceImpl.java
public class VendorExtendedConfigServiceImpl extends ServiceImpl<VendorExtendedConfigMapper, VendorExtendedConfig> implements VendorExtendedConfigService {

    private static final Logger log = LoggerFactory.getLogger(VendorExtendedConfigServiceImpl.class);
========
public class VendorConfigExtendedServiceImpl extends ServiceImpl<VendorConfigExtendedMapper, VendorConfigExtended>
    implements VendorConfigExtendedService {

    private static final Logger log = LoggerFactory.getLogger(VendorConfigExtendedServiceImpl.class);
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorConfigExtendedServiceImpl.java
    private static final String CONFIG_CACHE_PREFIX = "config:";
    private static final int CONFIG_CACHE_TTL_SECONDS = 3600;

    @Autowired
    private ConfigVersionMapper configVersionMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorExtendedConfigServiceImpl.java
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

        PageResult<VendorExtendedConfig> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    @Override
    public List<VendorExtendedConfig> getByVendor(Long vendorId) {
        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorExtendedConfig::getVendorId, vendorId);
        wrapper.eq(VendorExtendedConfig::getIsActive, true);
========
    public PageResult<VendorConfigExtended> list(Long vendorId, String keyword, int page, int pageSize) {
        LambdaQueryWrapper<VendorConfigExtended> wrapper = new LambdaQueryWrapper<>();
        if (vendorId != null) {
            wrapper.eq(VendorConfigExtended::getVendorId, vendorId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(VendorConfigExtended::getConfigKey, keyword);
        }
        wrapper.orderByDesc(VendorConfigExtended::getUpdatedAt);

        Page<VendorConfigExtended> result = this.page(new Page<>(page, pageSize), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    public List<VendorConfigExtended> getByVendor(Long vendorId) {
        LambdaQueryWrapper<VendorConfigExtended> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfigExtended::getVendorId, vendorId);
        wrapper.eq(VendorConfigExtended::getIsActive, true);
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorConfigExtendedServiceImpl.java
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

<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorExtendedConfigServiceImpl.java
        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorExtendedConfig::getConfigKey, configKey);
        wrapper.eq(VendorExtendedConfig::getIsActive, true);
        VendorExtendedConfig config = this.getOne(wrapper);
========
        LambdaQueryWrapper<VendorConfigExtended> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfigExtended::getConfigKey, configKey);
        wrapper.eq(VendorConfigExtended::getIsActive, true);
        VendorConfigExtended config = this.getOne(wrapper);
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorConfigExtendedServiceImpl.java

        if (config != null && config.getConfigValue() != null) {
            redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(),
                CONFIG_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            return config.getConfigValue();
        }

        return null;
    }

    @Override
    public boolean updateConfig(String configKey, String configValue, Long updatedBy) {
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorExtendedConfigServiceImpl.java
        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorExtendedConfig::getConfigKey, configKey);
        VendorExtendedConfig oldConfig = this.getOne(wrapper);

        if (oldConfig != null) {
            saveVersion(oldConfig);
        }

        if (oldConfig != null) {
========
        LambdaQueryWrapper<VendorConfigExtended> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfigExtended::getConfigKey, configKey);
        VendorConfigExtended oldConfig = this.getOne(wrapper);

        if (oldConfig != null) {
            saveVersion(oldConfig);
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorConfigExtendedServiceImpl.java
            oldConfig.setConfigValue(configValue);
            oldConfig.setUpdatedBy(updatedBy);
            this.updateById(oldConfig);
        } else {
<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorExtendedConfigServiceImpl.java
            VendorExtendedConfig newConfig = new VendorExtendedConfig();
========
            VendorConfigExtended newConfig = new VendorConfigExtended();
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorConfigExtendedServiceImpl.java
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

<<<<<<<< HEAD:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorExtendedConfigServiceImpl.java
    private void saveVersion(VendorExtendedConfig config) {
========
    private void saveVersion(VendorConfigExtended config) {
>>>>>>>> ad42169c9e9c75e570e42951b17dbb935cbc5a7f:data-platform-vendor/data-platform-vendor-service/src/main/java/com/dataplatform/vendor/service/impl/VendorConfigExtendedServiceImpl.java
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
