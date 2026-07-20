package com.dataplatform.governance.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("service_health_check")
public class ServiceHealthCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String serviceName;
    private Boolean healthy;
    private Long responseTime;
    private Integer instanceCount;
    private LocalDateTime checkedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public Boolean getHealthy() { return healthy; }
    public void setHealthy(Boolean healthy) { this.healthy = healthy; }
    public Long getResponseTime() { return responseTime; }
    public void setResponseTime(Long responseTime) { this.responseTime = responseTime; }
    public Integer getInstanceCount() { return instanceCount; }
    public void setInstanceCount(Integer instanceCount) { this.instanceCount = instanceCount; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}
