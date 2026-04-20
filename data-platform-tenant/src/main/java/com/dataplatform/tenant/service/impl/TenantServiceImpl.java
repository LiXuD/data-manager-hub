package com.dataplatform.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.tenant.entity.TenantInfo;
import com.dataplatform.tenant.mapper.TenantMapper;
import com.dataplatform.tenant.service.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
}