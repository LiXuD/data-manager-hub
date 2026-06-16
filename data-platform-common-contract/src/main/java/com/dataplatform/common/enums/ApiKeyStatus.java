package com.dataplatform.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * API Key 状态枚举
 */
public enum ApiKeyStatus implements CodeEnum {
    ACTIVE("active", "有效"),
    EXPIRED("expired", "已过期"),
    REVOKED("revoked", "已吊销");
    private final String code;
    private final String description;
    ApiKeyStatus(String code, String description) {
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
    public static ApiKeyStatus fromCode(String code) {
        return EnumUtils.fromCode(ApiKeyStatus.class, code);
    }
}
