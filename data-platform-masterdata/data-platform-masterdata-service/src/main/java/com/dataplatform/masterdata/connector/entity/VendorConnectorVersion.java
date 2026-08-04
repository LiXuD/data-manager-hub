package com.dataplatform.masterdata.connector.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dataplatform.common.handler.JsonbTypeHandler;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName(value = "vendor_connector_version", autoResultMap = true)
public class VendorConnectorVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long vendorConfigId;
    private Integer versionNo;
    private Integer draftVersion;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String pipelineSnapshot;
    private String snapshotHash;
    private Integer securityVersion;
    private String status;
    private Long previousVersionId;
    private LocalDateTime publishedAt;
    private Long publishedBy;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
