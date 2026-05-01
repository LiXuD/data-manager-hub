package com.dataplatform.iam.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.iam.entity.User;
import com.dataplatform.iam.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserMapper userMapper;

    @OperationLog(module = "认证管理", operation = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return Result.error(400, "用户名和密码不能为空");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getStatus, "active"));

        if (user == null || !password.equals(user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }

        UserContext.login(user.getId(), user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("token", StpUtil.getTokenValue());
        data.put("username", user.getUsername());
        data.put("userId", user.getId());

        return Result.success(data);
    }

    @OperationLog(module = "认证管理", operation = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        UserContext.logout();
        return Result.success(null);
    }

    @GetMapping("/verify")
    public Result<Map<String, Object>> verify() {
        if (!UserContext.isLoggedIn()) {
            return Result.error(401, "未登录或会话已过期");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("valid", true);
        data.put("userId", UserContext.getCurrentUserId());
        data.put("username", UserContext.getCurrentUsername());

        return Result.success(data);
    }

    @GetMapping("/userinfo")
    public Result<Map<String, Object>> getUserInfo() {
        if (!UserContext.isLoggedIn()) {
            return Result.error(401, "未登录");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId", UserContext.getCurrentUserId());
        data.put("username", UserContext.getCurrentUsername());

        return Result.success(data);
    }
}
