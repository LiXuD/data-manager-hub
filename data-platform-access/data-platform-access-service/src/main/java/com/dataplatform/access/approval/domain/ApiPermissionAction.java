package com.dataplatform.access.approval.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("api_permission_action")
public class ApiPermissionAction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private String action;
    private String actorType;
    private Long actorUserId;
    private String actorNameSnapshot;
    private String fromStatus;
    private String toStatus;
    private String comment;
    private String engineType;
    private String processInstanceId;
    private String taskId;
    private String taskDefinitionKey;
    private String taskName;
    private String taskAssignee;
    private Integer processDefinitionVersion;
    private String traceId;
    private LocalDateTime createdAt;
}
