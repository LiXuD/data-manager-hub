package com.dataplatform.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 灰度规则状态枚举
 */
public enum GrayRuleStatus implements CodeEnum {
    ACTIVE("active", "启用中"),
    INACTIVE("inactive", "已禁用"),
    EXPIRED("expired", "已过期"),
    PENDING("pending", "待生效");

    @EnumValue
    private final String code;
    private final String description;

    GrayRuleStatus(String code, String description) {
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

    public static GrayRuleStatus fromCode(String code) {
        return EnumUtils.fromCode(GrayRuleStatus.class, code);
    }
}
