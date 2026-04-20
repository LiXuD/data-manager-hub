package com.dataplatform.datatype.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("datatype_info")
public class DataTypeInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String datatypeCode;

    private String datatypeName;

    private String datatypeType;

    private String status;

    private String contactPerson;

    private String contactPhone;

    private String contactEmail;

    private Integer maxApiKeys;

    private Integer maxCallers;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean deleted;

    private Long updatedBy;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDataTypeCode() { return datatypeCode; }
    public void setDataTypeCode(String datatypeCode) { this.datatypeCode = datatypeCode; }

    public String getDataTypeName() { return datatypeName; }
    public void setDataTypeName(String datatypeName) { this.datatypeName = datatypeName; }

    public String getDataTypeType() { return datatypeType; }
    public void setDataTypeType(String datatypeType) { this.datatypeType = datatypeType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public Integer getMaxApiKeys() { return maxApiKeys; }
    public void setMaxApiKeys(Integer maxApiKeys) { this.maxApiKeys = maxApiKeys; }

    public Integer getMaxCallers() { return maxCallers; }
    public void setMaxCallers(Integer maxCallers) { this.maxCallers = maxCallers; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}