package com.dataplatform.masterdata.connector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dataplatform.common.handler.JsonbTypeHandler;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Immutable, payload-free evidence of one controlled connector draft test. */
@Getter
@Setter
@TableName(value = "vendor_connector_test_fact", autoResultMap = true)
public class VendorConnectorTestFact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long vendorConfigId;
    private Integer draftVersion;
    private String snapshotHash;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String pluginBindings;
    private Boolean testSucceeded;
    private String safeErrorCategory;
    private String safeErrorCode;
    private String resultDigest;
    private Long testedBy;
    private LocalDateTime testedAt;
}
