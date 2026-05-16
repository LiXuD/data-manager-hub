package com.dataplatform.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 灰度规则条件类型枚举
 */
public enum ConditionType implements CodeEnum {
    RANDOM("random", "随机流量"),
    USER_ID("userId", "用户ID"),
    IP("ip", "IP段"),
    COOKIE("cookie", "Cookie");
    private final String code;
    private final String description;
    ConditionType(String code, String description) {
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
    public static ConditionType fromCode(String code) {
        return EnumUtils.fromCode(ConditionType.class, code);
    }
}
