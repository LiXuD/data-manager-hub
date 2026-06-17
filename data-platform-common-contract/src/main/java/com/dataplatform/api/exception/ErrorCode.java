package com.dataplatform.api.exception;

/**
 * 公共契约层的 Error Code。
 * <p>业务枚举，统一约束状态、类型或策略编码。</p>
 */
public enum ErrorCode {
    // 通用错误码
    SUCCESS(200, "success"),
    SYSTEM_ERROR(500, "系统异常"),
    PARAM_ERROR(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问");

    private final Integer code;
    private final String msg;

    ErrorCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}