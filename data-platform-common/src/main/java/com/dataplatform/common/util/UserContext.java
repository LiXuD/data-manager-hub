package com.dataplatform.common.util;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;

/**
 * Sa-Token 用户上下文工具类
 */
public final class UserContext {

    private UserContext() {}

    public static final String USERNAME_KEY = "username";

    /**
     * 获取当前登录用户ID
     * @return 用户ID，未登录返回null
     */
    public static Long getCurrentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId != null ? Long.valueOf(loginId.toString()) : null;
    }

    /**
     * 获取当前登录用户名
     * @return 用户名，未登录或未设置返回null
     */
    public static String getCurrentUsername() {
        SaSession session = StpUtil.getSession(false);
        if (session == null) {
            return null;
        }
        Object username = session.get(USERNAME_KEY);
        return username != null ? username.toString() : null;
    }

    /**
     * 判断当前用户是否已登录
     * @return true-已登录，false-未登录
     */
    public static boolean isLoggedIn() {
        return StpUtil.isLogin();
    }

    /**
     * 用户登录并设置会话信息
     * @param userId 用户ID
     * @param username 用户名
     */
    public static void login(Long userId, String username) {
        StpUtil.login(userId);
        StpUtil.getSession().set(USERNAME_KEY, username);
    }

    /**
     * 用户登出
     */
    public static void logout() {
        StpUtil.logout();
    }
}
