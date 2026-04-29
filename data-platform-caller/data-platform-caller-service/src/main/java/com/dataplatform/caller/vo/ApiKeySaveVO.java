package com.dataplatform.caller.vo;

// import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * API Key保存请求
 */

public class ApiKeySaveVO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private Long callerId;
    
    private Integer rateLimit;
    
    private Integer quotaLimit;
    
    private String expireTime;

    // Getters and Setters
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public Integer getRateLimit() { return rateLimit; }
    public void setRateLimit(Integer rateLimit) { this.rateLimit = rateLimit; }
    public Integer getQuotaLimit() { return quotaLimit; }
    public void setQuotaLimit(Integer quotaLimit) { this.quotaLimit = quotaLimit; }
    public String getExpireTime() { return expireTime; }
    public void setExpireTime(String expireTime) { this.expireTime = expireTime; }

}