package com.dataplatform.user.controller;

import com.dataplatform.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Map<String, String> USERS = new HashMap<>();

    static {
        USERS.put("admin", "admin123");
        USERS.put("test", "test123");
    }

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null) {
            return Result.error(400, "用户名和密码不能为空");
        }

        String storedPassword = USERS.get(username);
        if (storedPassword == null || !storedPassword.equals(password)) {
            return Result.error(401, "用户名或密码错误");
        }

        // 生成简单 token
        String token = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        data.put("username", username);

        return Result.success(data);
    }

    /**
     * 验证 token
     */
    @GetMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error(401, "无效的认证头");
        }

        String token = authHeader.substring(7);
        if (token.length() < 10) {
            return Result.error(401, "无效的 token");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("valid", true);
        data.put("username", "admin");

        return Result.success(data);
    }
}