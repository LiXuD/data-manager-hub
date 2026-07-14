package com.dataplatform.common.enums;
/**
 * 错误码枚举
 */
public enum ErrorCode {
    // 成功
    SUCCESS(200, "成功"),
    // 参数错误 1001-1999
    INVALID_PARAMETER(1001, "参数错误"),
    MISSING_PARAMETER(1002, "缺少必要参数"),
    // 认证授权错误 2001-2999
    INVALID_API_KEY(2001, "无效API Key"),
    API_KEY_EXPIRED(2002, "API Key已过期"),
    INSUFFICIENT_QUOTA(2003, "额度不足"),
    VENDOR_NOT_FOUND(2004, "厂商不存在"),
    DATA_TYPE_NOT_FOUND(2005, "数据类型不存在"),
    // 厂商调用错误 3001-3999
    VENDOR_TIMEOUT(3001, "厂商响应超时"),
    VENDOR_ERROR(3002, "厂商返回错误"),
    VENDOR_UNAVAILABLE(3003, "厂商不可用"),
    // 系统内部错误 4001-4999
    INTERNAL_ERROR(4001, "系统内部错误"),
    // 限流/熔断错误 5001-5999
    RATE_LIMIT_EXCEEDED(5001, "超过速率限制"),
    CIRCUIT_BREAKER_OPEN(5002, "熔断器开启");
    private final Integer code;
    private final String message;
    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    public Integer getCode() { return code; }
    public String getMessage() { return message; }
}
