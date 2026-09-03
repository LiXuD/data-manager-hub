package com.dataplatform.access.call.service;

/**
 * Expected failures while resolving or charging an OpenAPI request.
 *
 * <p>Dependency and policy failures must remain distinguishable from an
 * unexpected programming error.  The controller maps this exception to a
 * structured response without exposing dependency details.</p>
 */
public final class OpenApiQueryException extends RuntimeException {

    private final int status;
    private final String errorCode;

    private OpenApiQueryException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static OpenApiQueryException badRequest(String errorCode, String message) {
        return new OpenApiQueryException(400, errorCode, message);
    }

    public static OpenApiQueryException badGateway(String errorCode, String message) {
        return new OpenApiQueryException(502, errorCode, message);
    }

    public static OpenApiQueryException serviceUnavailable(String errorCode, String message) {
        return new OpenApiQueryException(503, errorCode, message);
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
