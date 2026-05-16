package com.dataplatform.common.util;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 用户上下文工具类
 */
public final class UserContext {

    private UserContext() {}

    public static final String USERNAME_KEY = "username";
    public static final String TENANT_ID_KEY = "tenantId";
    public static final String PERMISSIONS_KEY = "permissions";

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
     * 获取当前租户ID
     * @return 租户ID，未设置返回null
     */
    public static Long getCurrentTenantId() {
        SaSession session = StpUtil.getSession(false);
        if (session == null) {
            return null;
        }
        Object tenantId = session.get(TENANT_ID_KEY);
        return tenantId != null ? Long.valueOf(tenantId.toString()) : null;
    }

    /**
     * 获取当前用户权限列表
     * @return 权限列表，未设置返回空列表
     */
    @SuppressWarnings("unchecked")
    public static List<String> getCurrentPermissions() {
        SaSession session = StpUtil.getSession(false);
        if (session == null) {
            return new ArrayList<>();
        }
        Object permissions = session.get(PERMISSIONS_KEY);
        if (permissions instanceof List) {
            return (List<String>) permissions;
        }
        return new ArrayList<>();
    }

    /**
     * 判断当前用户是否有指定权限
     * @param permission 权限标识
     * @return true-有此权限，false-无此权限
     */
    public static boolean hasPermission(String permission) {
        List<String> permissions = getCurrentPermissions();
        return permissions.contains(permission);
    }

    /**
     * 判断当前用户是否登录
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
        login(userId, username, null, null);
    }

    /**
     * 用户登录并设置会话信息
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param permissions 权限列表
     */
    public static void login(Long userId, String username, Long tenantId, List<String> permissions) {
        StpUtil.login(userId);
        StpUtil.getSession().set(USERNAME_KEY, username);
        if (tenantId != null) {
            StpUtil.getSession().set(TENANT_ID_KEY, tenantId);
        }
        if (permissions != null && !permissions.isEmpty()) {
            StpUtil.getSession().set(PERMISSIONS_KEY, permissions);
        }
    }

    /**
     * 用户登出
     */
    public static void logout() {
        StpUtil.logout();
    }
}
