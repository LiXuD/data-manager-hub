package com.dataplatform.identity.iam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.handler.CommonStatusTypeHandler;
import java.time.LocalDateTime;

/**
 * 身份租户域用户权限的 User。
 * <p>数据库实体对象，映射业务表字段并承载持久化层数据结构。</p>
 */
@TableName(value = "user_info", autoResultMap = true)
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String nickname;
    private String password;
    private String email;
    private String phone;
    @TableField(typeHandler = CommonStatusTypeHandler.class)
    private CommonStatus status;
    private Long tenantId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic
    private Boolean deleted;

    @TableField(exist = false)
    private String realName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public CommonStatus getStatus() { return status; }
    public void setStatus(CommonStatus status) { this.status = status; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    public String getRealName() { return nickname; }
    public void setRealName(String realName) { this.nickname = realName; }
}
