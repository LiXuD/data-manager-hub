package com.dataplatform.masterdata.connector.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("vendor_connector_migration")
public class VendorConnectorMigration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long vendorConfigId;
    private Long vendorId;
    private Long interfaceId;
    private String state;
    private Integer recordVersion;
    private String sourceConfigHash;
    private Long draftId;
    private Integer draftVersion;
    private String draftSnapshotHash;
    private Long publishedConnectorVersionId;
    private Integer publishedVersionNo;
    private String previousRuntimeMode;
    private Long previousActiveConnectorVersionId;
    private Integer previousConnectorVersion;
    private Integer minimumObservationMinutes;
    private Long minimumCalls;
    private Double maximumErrorRate;
    private Long maximumP95DurationMs;
    private Double minimumBillingCoverageRate;
    private LocalDateTime observationStartedAt;
    private LocalDateTime observationEligibleAt;
    private Long observedCalls;
    private Long observedSuccesses;
    private Long observedFailures;
    private Double observedErrorRate;
    private Long observedP95DurationMs;
    private Long observedCacheHits;
    private Long observedRealtimeCalls;
    private Long observedBillingEvents;
    private Long observedPostedBillingEvents;
    private Double observedBillingCoverageRate;
    private BigDecimal observedBillingAmount;
    private Boolean observationGatePassed;
    private String safeErrorCode;
    private String safeErrorDigest;
    private LocalDateTime completedAt;
    private LocalDateTime rolledBackAt;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
