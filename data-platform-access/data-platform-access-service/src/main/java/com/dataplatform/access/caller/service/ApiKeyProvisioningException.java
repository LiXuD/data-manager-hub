package com.dataplatform.access.caller.service;

import org.springframework.http.HttpStatus;

/** Structured failures for the atomic API Key and grant provisioning flow. */
public final class ApiKeyProvisioningException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    private ApiKeyProvisioningException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static ApiKeyProvisioningException conflict(String errorCode, String message) {
        return new ApiKeyProvisioningException(HttpStatus.CONFLICT, errorCode, message);
    }

    public static ApiKeyProvisioningException serviceUnavailable(String errorCode, String message) {
        return new ApiKeyProvisioningException(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
