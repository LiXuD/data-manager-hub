package com.dataplatform.identity.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.identity.tenant.entity.TenantInfo;
import com.dataplatform.identity.tenant.mapper.TenantMapper;
import com.dataplatform.identity.tenant.service.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Service
public class TenantServiceImpl extends ServiceImpl<TenantMapper, TenantInfo> implements TenantService {

    @Override
    public Page<TenantInfo> listPage(int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<TenantInfo> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                .like(TenantInfo::getTenantCode, keyword)
                .or()
                .like(TenantInfo::getTenantName, keyword));
        }
        
        if (StringUtils.hasText(status)) {
            wrapper.eq(TenantInfo::getStatus, status);
        }
        
        wrapper.orderByDesc(TenantInfo::getCreatedAt);
        
        return page(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public TenantInfo getByTenantCode(String tenantCode) {
        return this.getOne(new LambdaQueryWrapper<TenantInfo>()
            .eq(TenantInfo::getTenantCode, tenantCode)
            .eq(TenantInfo::getDeleted, false));
    }
}