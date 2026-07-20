package com.dataplatform.identity.api.dto;

import java.io.Serializable;

/**
 * 身份租户域的 Login Req DTO。
 * <p>跨服务契约数据对象，用于 api 模块暴露远程接口时传递稳定字段。</p>
 */
public class LoginReqDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
