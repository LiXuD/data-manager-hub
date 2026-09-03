package com.dataplatform.billing.service;

import java.util.List;

/** Structured, expected failures from the billing-plan lifecycle. */
public final class BillingPlanException extends IllegalArgumentException {

    private final int status;
    private final String errorCode;
    private final List<String> errors;

    private BillingPlanException(int status, String errorCode, String message, List<String> errors) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static BillingPlanException badRequest(String message) {
        return new BillingPlanException(400, "BILLING_PLAN_INVALID", message, List.of(message));
    }

    public static BillingPlanException validation(List<String> errors) {
        List<String> safeErrors = errors == null ? List.of("计费方案校验失败") : errors;
        return new BillingPlanException(400, "BILLING_PLAN_VALIDATION_FAILED",
                String.join("；", safeErrors), safeErrors);
    }

    public static BillingPlanException notFound(String message) {
        return new BillingPlanException(404, "BILLING_PLAN_NOT_FOUND", message, List.of(message));
    }

    public static BillingPlanException conflict(String code, String message) {
        return new BillingPlanException(409, code, message, List.of(message));
    }

    public static BillingPlanException unavailable(String message) {
        return new BillingPlanException(503, "BILLING_DEPENDENCY_UNAVAILABLE", message, List.of(message));
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<String> getErrors() {
        return errors;
    }
}
