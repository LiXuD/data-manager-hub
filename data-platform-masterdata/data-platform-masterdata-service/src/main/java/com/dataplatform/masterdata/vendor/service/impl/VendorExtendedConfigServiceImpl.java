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
import com.dataplatform.api.Result;
import com.dataplatform.identity.api.dto.EncryptionReqDTO;
import com.dataplatform.identity.api.feign.EncryptionInternalFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Objects;

/**
 * 主数据域厂商的 Vendor Extended Config Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
@Service
public class VendorExtendedConfigServiceImpl extends ServiceImpl<VendorExtendedConfigMapper, VendorExtendedConfig> implements VendorExtendedConfigService {

    private static final Logger log = LoggerFactory.getLogger(VendorExtendedConfigServiceImpl.class);
    private static final String CONFIG_CACHE_PREFIX = "config:";
    private static final int CONFIG_CACHE_TTL_SECONDS = 3600;
    private static final int SENSITIVE_CONFIG_CACHE_TTL_SECONDS = 300;

    @Autowired
    private ConfigVersionMapper configVersionMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private EncryptionInternalFeignClient encryptionClient;

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
        return PageResult.of(result.getRecords().stream().map(this::maskForDisplay).toList(),
                result.getTotal(), page, pageSize);
    }

    @Override
    public List<VendorExtendedConfig> getByVendor(Long vendorId) {
        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorExtendedConfig::getVendorId, vendorId);
        wrapper.eq(VendorExtendedConfig::getIsActive, true);
        return list(wrapper).stream().map(this::maskForDisplay).toList();
    }

    @Override
    public VendorExtendedConfig getForDisplay(Long id) {
        return maskForDisplay(getById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VendorExtendedConfig saveSecure(VendorExtendedConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        VendorExtendedConfig stored = copy(config);
        if (Boolean.TRUE.equals(stored.getIsEncrypted()) && stored.getConfigValue() != null) {
            stored.setConfigValue(encrypt(stored.getConfigValue()));
        }
        if (!save(stored)) {
            throw new IllegalStateException("厂商扩展配置保存失败");
        }
        return maskForDisplay(stored);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VendorExtendedConfig updateSecure(Long id, VendorExtendedConfig config) {
        VendorExtendedConfig existing = getById(id);
        if (existing == null) {
            return null;
        }
        saveVersion(existing);
        VendorExtendedConfig updated = merge(existing, config);
        boolean targetEncrypted = Boolean.TRUE.equals(updated.getIsEncrypted());
        String supplied = config == null ? null : config.getConfigValue();
        if (supplied == null || supplied.isBlank() || "••••••••".equals(supplied)) {
            if (!Objects.equals(existing.getIsEncrypted(), updated.getIsEncrypted())) {
                String plain = Boolean.TRUE.equals(existing.getIsEncrypted())
                        ? decodeStoredValue(existing) : existing.getConfigValue();
                updated.setConfigValue(targetEncrypted ? encrypt(plain) : plain);
            } else {
                if (targetEncrypted && existing.getConfigValue() != null
                        && !isCiphertext(existing.getConfigValue())) {
                    throw new IllegalStateException("敏感配置不是受支持的密文格式");
                }
                updated.setConfigValue(existing.getConfigValue());
            }
        } else {
            updated.setConfigValue(targetEncrypted ? encrypt(supplied) : supplied);
        }
        if (!updateById(updated)) {
            throw new IllegalStateException("厂商扩展配置更新失败");
        }
        evict(existing.getVendorId(), existing.getConfigKey());
        evict(updated.getVendorId(), updated.getConfigKey());
        return maskForDisplay(updated);
    }

    @Override
    public boolean removeSecure(Long id) {
        VendorExtendedConfig existing = getById(id);
        if (existing == null || !removeById(id)) {
            return false;
        }
        evict(existing.getVendorId(), existing.getConfigKey());
        return true;
    }

    @Override
    public boolean updateStatusSecure(Long id, String status) {
        VendorExtendedConfig existing = getById(id);
        if (existing == null) {
            return false;
        }
        VendorExtendedConfig update = new VendorExtendedConfig();
        update.setId(id);
        update.setStatus(status);
        update.setIsActive("active".equals(status));
        if (!updateById(update)) {
            return false;
        }
        evict(existing.getVendorId(), existing.getConfigKey());
        return true;
    }

    @Override
    public String getConfig(String configKey) {
        return getConfig(null, configKey);
    }

    @Override
    public String getConfig(Long vendorId, String configKey) {
        String cacheKey = CONFIG_CACHE_PREFIX + (vendorId == null ? "global" : vendorId) + ":" + configKey;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            log.debug("配置命中缓存: {}", configKey);
            return cachedValue;
        }

        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorExtendedConfig::getConfigKey, configKey);
        wrapper.eq(vendorId != null, VendorExtendedConfig::getVendorId, vendorId);
        wrapper.eq(VendorExtendedConfig::getIsActive, true);
        VendorExtendedConfig config = this.getOne(wrapper);

        if (config != null && config.getConfigValue() != null) {
            boolean encrypted = Boolean.TRUE.equals(config.getIsEncrypted());
            String value = encrypted ? decodeStoredValue(config) : config.getConfigValue();
            redisTemplate.opsForValue().set(cacheKey, value,
                    encrypted ? SENSITIVE_CONFIG_CACHE_TTL_SECONDS : CONFIG_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS);
            return value;
        }

        return null;
    }

    @Override
    public String getDisplayValue(String configKey) {
        VendorExtendedConfig config = getOne(new LambdaQueryWrapper<VendorExtendedConfig>()
                .eq(VendorExtendedConfig::getConfigKey, configKey).last("LIMIT 1"));
        if (config == null) {
            return null;
        }
        return Boolean.TRUE.equals(config.getIsEncrypted()) ? "••••••••" : config.getConfigValue();
    }

    @Override
    public boolean updateConfig(String configKey, String configValue, Long updatedBy) {
        LambdaQueryWrapper<VendorExtendedConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorExtendedConfig::getConfigKey, configKey);
        VendorExtendedConfig oldConfig = this.getOne(wrapper);

        if (oldConfig != null) {
            saveVersion(oldConfig);
            oldConfig.setConfigValue(Boolean.TRUE.equals(oldConfig.getIsEncrypted())
                    ? encrypt(configValue) : configValue);
            oldConfig.setUpdatedBy(updatedBy);
            this.updateById(oldConfig);
        } else {
            VendorExtendedConfig newConfig = new VendorExtendedConfig();
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(configValue);
            newConfig.setIsActive(true);
            this.save(newConfig);
        }

        evict(oldConfig == null ? null : oldConfig.getVendorId(), configKey);
        log.info("配置已更新: {}, 缓存已清除", configKey);

        return true;
    }

    @Override
    public boolean publishConfig(String configKey) {
        VendorExtendedConfig config = getOne(new LambdaQueryWrapper<VendorExtendedConfig>()
                .eq(VendorExtendedConfig::getConfigKey, configKey).last("LIMIT 1"));
        if (config != null && Boolean.TRUE.equals(config.getIsEncrypted())) {
            log.warn("拒绝将敏感配置发布到通用配置通道: {}", configKey);
            return false;
        }
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
        VendorExtendedConfig current = getOne(new LambdaQueryWrapper<VendorExtendedConfig>()
                .eq(VendorExtendedConfig::getConfigKey, configKey).last("LIMIT 1"));
        if (current == null) {
            return false;
        }
        saveVersion(current);
        current.setConfigValue(version.getConfigValue());
        current.setUpdatedBy(null);
        updateById(current);
        evict(current.getVendorId(), current.getConfigKey());
        return true;
    }

    @Override
    public List<ConfigVersion> getVersionHistory(String configKey) {
        LambdaQueryWrapper<ConfigVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigVersion::getConfigKey, configKey);
        wrapper.orderByDesc(ConfigVersion::getCreatedAt);
        List<ConfigVersion> versions = configVersionMapper.selectList(wrapper);
        VendorExtendedConfig current = getOne(new LambdaQueryWrapper<VendorExtendedConfig>()
                .eq(VendorExtendedConfig::getConfigKey, configKey).last("LIMIT 1"));
        if (current != null && Boolean.TRUE.equals(current.getIsEncrypted())) {
            versions.forEach(version -> version.setConfigValue("••••••••"));
        }
        return versions;
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
        if (configVersionMapper.insert(version) != 1) {
            throw new IllegalStateException("厂商配置版本保存失败");
        }
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

    private String encrypt(String plainText) {
        EncryptionReqDTO request = new EncryptionReqDTO();
        request.setText(plainText);
        request.setTableName("vendor_config_extended");
        Result<String> result = encryptionClient.encrypt(request);
        if (result == null || result.getData() == null) {
            throw new IllegalStateException("敏感配置加密失败");
        }
        return result.getData();
    }

    private String decrypt(String encryptedText) {
        EncryptionReqDTO request = new EncryptionReqDTO();
        request.setText(encryptedText);
        request.setTableName("vendor_config_extended");
        Result<String> result = encryptionClient.decrypt(request);
        if (result == null || result.getData() == null) {
            throw new IllegalStateException("敏感配置解密失败");
        }
        return result.getData();
    }

    private String decodeStoredValue(VendorExtendedConfig config) {
        String storedValue = config.getConfigValue();
        if (!isCiphertext(storedValue)) {
            throw new IllegalStateException("敏感配置不是受支持的密文格式");
        }
        return decrypt(storedValue);
    }

    private boolean isCiphertext(String value) {
        return value != null && value.matches("^v1:[1-9][0-9]*:.+$");
    }

    private VendorExtendedConfig maskForDisplay(VendorExtendedConfig source) {
        if (source == null) {
            return null;
        }
        VendorExtendedConfig copy = copy(source);
        if (Boolean.TRUE.equals(copy.getIsEncrypted()) && copy.getConfigValue() != null) {
            copy.setConfigValue("••••••••");
        }
        return copy;
    }

    private VendorExtendedConfig copy(VendorExtendedConfig source) {
        VendorExtendedConfig target = new VendorExtendedConfig();
        target.setId(source.getId());
        target.setVendorId(source.getVendorId());
        target.setConfigKey(source.getConfigKey());
        target.setConfigValue(source.getConfigValue());
        target.setConfigType(source.getConfigType());
        target.setDescription(source.getDescription());
        target.setIsEncrypted(source.getIsEncrypted());
        target.setIsActive(source.getIsActive());
        target.setStatus(source.getStatus());
        target.setUpdatedBy(source.getUpdatedBy());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private VendorExtendedConfig merge(VendorExtendedConfig existing, VendorExtendedConfig changes) {
        if (changes == null) {
            return copy(existing);
        }
        VendorExtendedConfig target = copy(existing);
        if (changes.getVendorId() != null) target.setVendorId(changes.getVendorId());
        if (StringUtils.hasText(changes.getConfigKey())) target.setConfigKey(changes.getConfigKey());
        if (changes.getConfigType() != null) target.setConfigType(changes.getConfigType());
        if (changes.getDescription() != null) target.setDescription(changes.getDescription());
        if (changes.getIsEncrypted() != null) target.setIsEncrypted(changes.getIsEncrypted());
        if (changes.getIsActive() != null) target.setIsActive(changes.getIsActive());
        if (changes.getStatus() != null) target.setStatus(changes.getStatus());
        if (changes.getUpdatedBy() != null) target.setUpdatedBy(changes.getUpdatedBy());
        return target;
    }

    private void evict(Long vendorId, String configKey) {
        redisTemplate.delete(CONFIG_CACHE_PREFIX + (vendorId == null ? "global" : vendorId) + ":" + configKey);
        redisTemplate.delete(CONFIG_CACHE_PREFIX + "global:" + configKey);
    }
}
