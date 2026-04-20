package com.dataplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.user.entity.User;
import com.dataplatform.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public PageResponse<User> list(String username, String status, int page, int pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(User::getUsername, username);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.eq(User::getDeleted, false);
        wrapper.orderByDesc(User::getCreatedAt);
        
        Page<User> result = userMapper.selectPage(new Page<>(page, pageSize), wrapper);
        
        PageResponse<User> response = new PageResponse<>();
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        return response;
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    public void create(User user) {
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setDeleted(false);
        userMapper.insert(user);
    }

    public void update(User user) {
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void delete(Long id) {
        User user = new User();
        user.setId(id);
        user.setDeleted(true);
        userMapper.updateById(user);
    }
}