package com.dataplatform.common.exception;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    private final Integer code;
    private final String message;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(String message) {
        this(4001, message);
    }

    public Integer getCode() { return code; }
    public String getMessage() { return message; }

    public static BusinessException of(Integer code, String message) {
        return new BusinessException(code, message);
    }
}