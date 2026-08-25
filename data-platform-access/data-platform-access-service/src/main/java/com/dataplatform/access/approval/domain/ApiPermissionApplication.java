package com.dataplatform.access.approval.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("api_permission_application")
public class ApiPermissionApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String applicationNo;
    private String requestType;
    private Long tenantId;
    private Long callerId;
    private String callerCodeSnapshot;
    private String callerNameSnapshot;
    private Long apiKeyId;
    private String apiKeyNameSnapshot;
    private Long applicantUserId;
    private String applicantNameSnapshot;
    private String businessPurpose;
    private String businessScene;
    private Long expectedDailyCalls;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ticketNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime requestedExpireAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedExpireAt;
    private String status;
    private String engineType;
    private String engineStatus;
    private String processDefinitionKey;
    private Integer processDefinitionVersion;
    private String processInstanceId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentTaskId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentTaskKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentTaskName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime currentTaskCreatedAt;
    private LocalDateTime submittedAt;
    private Long decidedBy;
    private String decidedByNameSnapshot;
    private LocalDateTime decidedAt;
    private String decisionComment;
    private String idempotencyKey;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
