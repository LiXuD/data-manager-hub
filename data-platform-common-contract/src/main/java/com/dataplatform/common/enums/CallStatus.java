package com.dataplatform.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 调用状态枚举
 * 用于：调用记录
 */
public enum CallStatus implements CodeEnum {
    SUCCESS("success", "成功"),
    FAILED("failed", "失败"),
    TIMEOUT("timeout", "超时"),
    RATE_LIMITED("rate_limited", "限流");
    private final String code;
    private final String description;
    CallStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    @Override
    @JsonValue
    public String getCode() {
        return code;
    }
    @Override
    public String getDescription() {
        return description;
    }
    public static CallStatus fromCode(String code) {
        return EnumUtils.fromCode(CallStatus.class, code);
    }
}
