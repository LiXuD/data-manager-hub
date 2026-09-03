package com.dataplatform.billing.service;

/** Structured failures for the vendor-bill reconciliation boundary. */
public final class BillingReconciliationException extends RuntimeException {

    private final int status;
    private final String errorCode;

    private BillingReconciliationException(int status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static BillingReconciliationException badRequest(String code, String message) {
        return new BillingReconciliationException(400, code, message, null);
    }

    public static BillingReconciliationException unavailable(String code, String message) {
        return new BillingReconciliationException(503, code, message, null);
    }

    public static BillingReconciliationException conflict(String code, String message) {
        return new BillingReconciliationException(409, code, message, null);
    }

    public static BillingReconciliationException invalidCsv(Throwable cause) {
        return new BillingReconciliationException(400, "BILLING_RECONCILIATION_INVALID_CSV",
                "厂商账单文件格式无效", cause);
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
