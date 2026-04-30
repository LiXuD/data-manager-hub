package com.dataplatform.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.iam.entity.Role;
import com.dataplatform.iam.mapper.RoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RoleService extends ServiceImpl<RoleMapper, Role> {

    public PageResult<Role> list(String keyword, String status, int page, int pageSize) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Role::getRoleName, keyword)
                   .or()
                   .like(Role::getRoleCode, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Role::getStatus, status);
        }
        wrapper.eq(Role::getDeleted, false);
        wrapper.orderByDesc(Role::getCreatedAt);

        Page<Role> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<Role> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    public Role getByRoleCode(String roleCode) {
        return this.getOne(new LambdaQueryWrapper<Role>()
            .eq(Role::getRoleCode, roleCode)
            .eq(Role::getDeleted, false));
    }
}
