package com.dataplatform.access.approval.api;

import org.springframework.http.HttpStatus;

public class ApiPermissionException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiPermissionException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
