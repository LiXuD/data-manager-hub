package com.dataplatform.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.config.entity.VendorConfig;
import com.dataplatform.config.mapper.VendorConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ConfigService extends ServiceImpl<VendorConfigMapper, VendorConfig> {

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
}