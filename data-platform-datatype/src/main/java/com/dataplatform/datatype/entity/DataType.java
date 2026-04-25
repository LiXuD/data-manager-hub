package com.dataplatform.datatype.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

@TableName("datatype_info")
public class DataType {
    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonProperty("datatypeCode")
    private String datatypeCode;

    @JsonProperty("datatypeName")
    private String datatypeName;

    private String description;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private Boolean deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDataTypeCode() { return datatypeCode; }
    public void setDataTypeCode(String datatypeCode) { this.datatypeCode = datatypeCode; }
    public String getDataTypeName() { return datatypeName; }
    public void setDataTypeName(String datatypeName) { this.datatypeName = datatypeName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
}