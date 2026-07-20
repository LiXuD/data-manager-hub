package com.dataplatform.access.caller.vo;

/** API Key 限流策略更新请求。 */
public class ApiKeyRateLimitUpdateVO {

    private Boolean rateLimitEnabled;
    private Integer rateLimit;

    public Boolean getRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(Boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public Integer getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Integer rateLimit) {
        this.rateLimit = rateLimit;
    }
}
