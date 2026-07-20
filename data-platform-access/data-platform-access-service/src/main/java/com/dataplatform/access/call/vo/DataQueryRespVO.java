package com.dataplatform.access.call.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 访问域数据调用的 Data Query Resp VO。
 * <p>Web 层请求或响应视图对象，用于隔离页面接口与数据库实体。</p>
 */
public class DataQueryRespVO {
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 响应数据(JSON)
     */
    private String data;
    
    /**
     * 是否使用缓存
     */
    private Boolean cached;
    
    /**
     * 耗时(毫秒)
     */
    private Long duration;
    
    /**
     * 成本(元)
     */
    private BigDecimal cost;
    
    /**
     * 响应时间
     */
    private LocalDateTime responseTime;

    // Getters and Setters
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public Boolean getCached() { return cached; }
    public void setCached(Boolean cached) { this.cached = cached; }
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public LocalDateTime getResponseTime() { return responseTime; }
    public void setResponseTime(LocalDateTime responseTime) { this.responseTime = responseTime; }

}