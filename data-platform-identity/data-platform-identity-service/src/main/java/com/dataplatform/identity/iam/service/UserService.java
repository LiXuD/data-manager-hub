package com.dataplatform.identity.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 身份租户域用户权限的 User Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    public PageResult<User> list(String keyword, String status, int page, int pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(User::getUsername, keyword)
                   .or()
                   .like(User::getNickname, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.eq(User::getDeleted, false);
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<User> response = new PageResult<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    public User getByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, username)
            .eq(User::getDeleted, false));
    }
}
