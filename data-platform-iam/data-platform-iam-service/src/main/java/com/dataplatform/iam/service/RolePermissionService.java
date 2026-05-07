package com.dataplatform.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.iam.entity.Permission;
import com.dataplatform.iam.entity.RolePermission;
import com.dataplatform.iam.mapper.RolePermissionMapper;
import com.dataplatform.iam.mapper.PermissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RolePermissionService extends ServiceImpl<RolePermissionMapper, RolePermission> {

    @Autowired
    private PermissionMapper permissionMapper;

    public List<Permission> getPermissionsByRoleId(Long roleId) {
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        List<Long> permissionIds = list(wrapper).stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return new ArrayList<>();
        }
        return permissionMapper.selectBatchIds(permissionIds);
    }

    public List<Long> getPermissionIdsByRoleId(Long roleId) {
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        return list(wrapper).stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        remove(wrapper);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<RolePermission> rolePermissions = permissionIds.stream()
                    .map(pid -> {
                        RolePermission rp = new RolePermission();
                        rp.setRoleId(roleId);
                        rp.setPermissionId(pid);
                        return rp;
                    })
                    .collect(Collectors.toList());
            saveBatch(rolePermissions);
        }
    }
}
