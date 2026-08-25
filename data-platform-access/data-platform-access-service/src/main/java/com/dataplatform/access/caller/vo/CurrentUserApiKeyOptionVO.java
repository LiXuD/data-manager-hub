package com.dataplatform.access.caller.vo;

/**
 * 当前登录用户可用于数据查询测试的 API Key 选项。
 */
public class CurrentUserApiKeyOptionVO {

    private final Long id;
    private final Long callerId;
    private final String callerCode;
    private final String callerName;
    private final String keyName;
    private final String maskedApiKey;

    public CurrentUserApiKeyOptionVO(
            Long id,
            Long callerId,
            String callerCode,
            String callerName,
            String keyName,
            String maskedApiKey) {
        this.id = id;
        this.callerId = callerId;
        this.callerCode = callerCode;
        this.callerName = callerName;
        this.keyName = keyName;
        this.maskedApiKey = maskedApiKey;
    }

    public Long getId() {
        return id;
    }

    public Long getCallerId() {
        return callerId;
    }

    public String getCallerCode() {
        return callerCode;
    }

    public String getCallerName() {
        return callerName;
    }

    public String getKeyName() {
        return keyName;
    }

    public String getMaskedApiKey() {
        return maskedApiKey;
    }
}
