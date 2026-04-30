package com.dataplatform.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.iam.entity.User;
import com.dataplatform.iam.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserMapper userMapper;

    @OperationLog(module = "认证管理", operation = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return Result.error(400, "用户名和密码不能为空");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getStatus, "active"));

        if (user == null) {
            return Result.error(401, "用户名或密码错误");
        }

        if (!password.equals(user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        data.put("username", username);
        data.put("userId", String.valueOf(user.getId()));

        return Result.success(data);
    }

    @GetMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error(401, "无效的认证头");
        }

        String token = authHeader.substring(7);
        if (token == null || token.trim().isEmpty() || token.length() < 10) {
            return Result.error(401, "无效的 token");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("valid", true);
        data.put("username", "verified-user");

        return Result.success(data);
    }
}
