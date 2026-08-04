package com.dataplatform.plugin.spi;

import java.util.Objects;

public class ConnectorException extends Exception {

    private final ErrorCategory category;
    private final String errorCode;
    private final String safeMessage;
    private final RequestDeliveryState deliveryState;

    public ConnectorException(ErrorCategory category, String errorCode, String safeMessage,
                              RequestDeliveryState deliveryState) {
        this(category, errorCode, safeMessage, deliveryState, null);
    }

    public ConnectorException(ErrorCategory category, String errorCode, String safeMessage,
                              RequestDeliveryState deliveryState, Throwable cause) {
        super(safeMessage, cause);
        this.category = Objects.requireNonNull(category, "category");
        this.errorCode = requireText(errorCode, "errorCode");
        this.safeMessage = requireText(safeMessage, "safeMessage");
        this.deliveryState = Objects.requireNonNull(deliveryState, "deliveryState");
    }

    public ErrorCategory category() { return category; }
    public String errorCode() { return errorCode; }
    public String safeMessage() { return safeMessage; }
    public RequestDeliveryState deliveryState() { return deliveryState; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
