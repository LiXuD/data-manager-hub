package com.dataplatform.common.enums;
/**
 * 计费类型枚举
 */
public enum BillingType {
    STANDARD("标准计费"),
    TIERED("阶梯计费"),
    DYNAMIC("动态计费");
    private final String description;
    BillingType(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }
    public static BillingType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        try {
            return valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
