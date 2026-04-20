package com.dataplatform.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.role.entity.RoleInfo;
import com.dataplatform.role.mapper.RoleMapper;
import com.dataplatform.role.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, RoleInfo> implements RoleService {

    @Override
    public Page<RoleInfo> listPage(int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<RoleInfo> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                .like(RoleInfo::getRoleCode, keyword)
                .or()
                .like(RoleInfo::getRoleName, keyword));
        }
        
        if (StringUtils.hasText(status)) {
            wrapper.eq(RoleInfo::getStatus, status);
        }
        
        wrapper.orderByDesc(RoleInfo::getCreatedAt);
        
        return page(new Page<>(page, pageSize), wrapper);
    }
}