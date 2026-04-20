package com.dataplatform.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.config.entity.VendorConfig;
import com.dataplatform.config.mapper.VendorConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConfigService {
    @Autowired
    private VendorConfigMapper configMapper;

    public PageResponse<VendorConfig> list(Long vendorId, String configKey, int page, int pageSize) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        if (vendorId != null) {
            wrapper.eq(VendorConfig::getVendorId, vendorId);
        }
        if (StringUtils.hasText(configKey)) {
            wrapper.like(VendorConfig::getConfigKey, configKey);
        }
        wrapper.orderByDesc(VendorConfig::getUpdatedAt);
        
        Page<VendorConfig> result = configMapper.selectPage(new Page<>(page, pageSize), wrapper);
        PageResponse<VendorConfig> response = new PageResponse<>();
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        return response;
    }

    public VendorConfig getById(Long id) {
        return configMapper.selectById(id);
    }

    public VendorConfig create(VendorConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.insert(config);
        return config;
    }

    public VendorConfig update(VendorConfig config) {
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);
        return config;
    }

    public void delete(Long id) {
        configMapper.deleteById(id);
    }

    public List<VendorConfig> getByVendor(Long vendorId) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getVendorId, vendorId);
        wrapper.eq(VendorConfig::getIsActive, true);
        return configMapper.selectList(wrapper);
    }
}
