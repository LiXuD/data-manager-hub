package com.dataplatform.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 通用状态枚举
 * 用于：厂商、数据类型、调用方、用户、角色、接口等
 */
public enum CommonStatus implements CodeEnum {
    ACTIVE("active", "启用"),
    INACTIVE("inactive", "禁用");
    private final String code;
    private final String description;
    CommonStatus(String code, String description) {
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
    public static CommonStatus fromCode(String code) {
        return EnumUtils.fromCode(CommonStatus.class, code);
    }
}
