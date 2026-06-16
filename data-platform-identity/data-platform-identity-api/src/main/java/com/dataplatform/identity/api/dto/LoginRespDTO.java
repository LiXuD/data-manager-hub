package com.dataplatform.identity.api.dto;

import java.io.Serializable;
import java.util.List;

public class LoginRespDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;
    private Long userId;
    private Long tenantId;
    private String username;
    private List<String> roles;
    private List<String> permissions;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}
