package com.dataplatform.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 启用状态枚举
 * 用于：配置项、开关等
 */
public enum EnableStatus implements CodeEnum {
    ENABLED("enabled", "已启用"),
    DISABLED("disabled", "已禁用");
    private final String code;
    private final String description;
    EnableStatus(String code, String description) {
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
    public static EnableStatus fromCode(String code) {
        return EnumUtils.fromCode(EnableStatus.class, code);
    }
}
