package com.dataplatform.masterdata.vendor.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dataplatform.common.handler.JsonbTypeHandler;
import java.time.LocalDateTime;

@TableName(value = "vendor_interface_security_version", autoResultMap = true)
public class VendorSecurityVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long vendorConfigId;
    private Integer versionNo;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String configSnapshot;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVendorConfigId() { return vendorConfigId; }
    public void setVendorConfigId(Long vendorConfigId) { this.vendorConfigId = vendorConfigId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getConfigSnapshot() { return configSnapshot; }
    public void setConfigSnapshot(String configSnapshot) { this.configSnapshot = configSnapshot; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
