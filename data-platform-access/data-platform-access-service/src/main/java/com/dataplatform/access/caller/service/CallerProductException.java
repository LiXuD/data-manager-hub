package com.dataplatform.access.caller.service;

/** Expected persistence conflicts for caller-product administration. */
public final class CallerProductException extends RuntimeException {

    private final int status;
    private final String errorCode;

    private CallerProductException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static CallerProductException conflict(String errorCode, String message) {
        return new CallerProductException(409, errorCode, message);
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
