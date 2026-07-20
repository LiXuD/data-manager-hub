package com.dataplatform.identity.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.identity.iam.entity.UserRole;
import com.dataplatform.identity.iam.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 身份租户域用户权限的 User Role Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class UserRoleService extends ServiceImpl<UserRoleMapper, UserRole> {

    public List<Long> getRoleIdsByUserId(Long userId) {
        return this.list(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId))
                .stream()
                .map(UserRole::getRoleId)
                .toList();
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        this.remove(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> userRoles = roleIds.stream()
                    .map(roleId -> {
                        UserRole userRole = new UserRole();
                        userRole.setUserId(userId);
                        userRole.setRoleId(roleId);
                        return userRole;
                    })
                    .toList();
            this.saveBatch(userRoles);
        }
    }
}