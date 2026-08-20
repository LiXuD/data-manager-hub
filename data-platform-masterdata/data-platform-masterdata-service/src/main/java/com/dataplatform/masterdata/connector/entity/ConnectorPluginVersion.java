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
@TableName(value = "connector_plugin_version", autoResultMap = true)
public class ConnectorPluginVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String pluginId;
    private String version;
    private String spiVersion;
    private String entryClass;
    private String artifactUri;
    private String artifactSha256;
    private String detachedSignature;
    private String signingKeyId;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String manifestJson;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String configSchemaJson;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String capabilities;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String permissionManifest;
    private String minHostVersion;
    private String manifestVersion;
    private String authoringModel;
    private String connectorKind;
    private String transportMode;
    private String outputMode;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String compatibilityManifest;
    private String status;
    private String safeErrorCode;
    private String safeErrorDigest;
    private LocalDateTime verifiedAt;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
