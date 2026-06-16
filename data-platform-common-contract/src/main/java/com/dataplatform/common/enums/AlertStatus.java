package com.dataplatform.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 告警规则状态枚举
 */
public enum AlertStatus implements CodeEnum {
    ACTIVE("active", "启用中"),
    INACTIVE("inactive", "已禁用"),
    FIRING("firing", "触发中"),
    RESOLVED("resolved", "已恢复");
    private final String code;
    private final String description;
    AlertStatus(String code, String description) {
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
    public static AlertStatus fromCode(String code) {
        return EnumUtils.fromCode(AlertStatus.class, code);
    }
}
