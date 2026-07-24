package com.dataplatform.identity.iam.security;

import org.springframework.http.HttpStatus;

public class IamAuthorizationException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public IamAuthorizationException(HttpStatus status, String errorCode, String message) {
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
