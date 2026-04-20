package com.dataplatform.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.role.entity.Role;
import com.dataplatform.role.mapper.RoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RoleService {
    @Autowired
    private RoleMapper roleMapper;

    public PageResponse<Role> list(String roleName, String status, int page, int pageSize) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(roleName)) {
            wrapper.like(Role::getRoleName, roleName);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Role::getStatus, status);
        }
        wrapper.eq(Role::getDeleted, false);
        wrapper.orderByDesc(Role::getCreatedAt);
        
        Page<Role> result = roleMapper.selectPage(new Page<>(page, pageSize), wrapper);
        
        PageResponse<Role> response = new PageResponse<>();
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        return response;
    }

    public Role getById(Long id) {
        return roleMapper.selectById(id);
    }

    public void create(Role role) {
        role.setCreatedAt(java.time.LocalDateTime.now());
        role.setDeleted(false);
        roleMapper.insert(role);
    }

    public void update(Role role) {
        role.setUpdatedAt(java.time.LocalDateTime.now());
        roleMapper.updateById(role);
    }

    public void delete(Long id) {
        Role role = new Role();
        role.setId(id);
        role.setDeleted(true);
        roleMapper.updateById(role);
    }
}