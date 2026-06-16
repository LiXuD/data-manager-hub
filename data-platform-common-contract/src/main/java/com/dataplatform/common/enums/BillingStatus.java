package com.dataplatform.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 账单状态枚举
 */
public enum BillingStatus implements CodeEnum {
    PENDING("pending", "待结算"),
    SETTLED("settled", "已结算"),
    OVERDUE("overdue", "逾期"),
    PAID("paid", "已支付");
    private final String code;
    private final String description;
    BillingStatus(String code, String description) {
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
    public static BillingStatus fromCode(String code) {
        return EnumUtils.fromCode(BillingStatus.class, code);
    }
}
