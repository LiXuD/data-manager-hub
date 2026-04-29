package com.dataplatform.monitor.enums;

/**
 * 告警规则类型枚举
 */
public enum AlertRuleType {

    /**
     * 厂商响应超时 (threshold: ms)
     */
    VENDOR_LATENCY("vendor_latency", "厂商响应超时", "ms"),

    /**
     * API调用失败率 (threshold: %)
     */
    VENDOR_ERROR("vendor_error", "API调用失败率", "%"),

    /**
     * 额度不足 (threshold: %)
     */
    QUOTA("quota", "额度不足", "%"),

    /**
     * 熔断触发
     */
    CIRCUIT("circuit", "熔断触发", "次");

    private final String code;
    private final String desc;
    private final String unit;

    AlertRuleType(String code, String desc, String unit) {
        this.code = code;
        this.desc = desc;
        this.unit = unit;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public String getUnit() {
        return unit;
    }

    public static AlertRuleType fromCode(String code) {
        for (AlertRuleType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}