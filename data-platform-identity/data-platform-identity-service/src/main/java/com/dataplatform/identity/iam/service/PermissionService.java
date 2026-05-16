package com.dataplatform.identity.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.mapper.PermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PermissionService extends ServiceImpl<PermissionMapper, Permission> {

    public PageResult<Permission> list(String keyword, String status, int page, int pageSize) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Permission::getPermissionName, keyword)
                    .or()
                    .like(Permission::getPermissionCode, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Permission::getStatus, status);
        }
        wrapper.eq(Permission::getDeleted, false);
        wrapper.orderByAsc(Permission::getParentId).orderByAsc(Permission::getSortOrder);

        PageResult<Permission> result = new PageResult<>();
        result.setData(page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize), wrapper).getRecords());
        result.setTotal(count(wrapper));
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }

    public List<Permission> listAllActive() {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getStatus, "active")
                .eq(Permission::getDeleted, false)
                .orderByAsc(Permission::getParentId)
                .orderByAsc(Permission::getSortOrder);
        return list(wrapper);
    }
}
