package com.dataplatform.access.approval.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("api_permission_application_item")
public class ApiPermissionApplicationItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long apiKeyId;
    private Long interfaceId;
    private String interfaceCodeSnapshot;
    private String interfaceNameSnapshot;
    private String interfaceStatusSnapshot;
    private String itemStatus;
    private Boolean requestedCacheEnabled;
    private Integer requestedCacheDays;
    private Boolean approvedCacheEnabled;
    private Integer approvedCacheDays;
    private Long grantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
