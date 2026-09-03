package com.dataplatform.access.call.service;

/**
 * 场景字典维护失败时返回给 HTTP 层的可解释业务异常。
 */
public class CallSceneException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public CallSceneException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static CallSceneException badRequest(String errorCode, String message) {
        return new CallSceneException(400, errorCode, message);
    }

    public static CallSceneException notFound(String errorCode, String message) {
        return new CallSceneException(404, errorCode, message);
    }

    public static CallSceneException conflict(String errorCode, String message) {
        return new CallSceneException(409, errorCode, message);
    }
}
