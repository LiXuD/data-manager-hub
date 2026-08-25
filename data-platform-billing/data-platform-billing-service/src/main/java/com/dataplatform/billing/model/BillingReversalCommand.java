package com.dataplatform.billing.model;

/** 对已入账事件执行一次全额冲正的管理命令。 */
public class BillingReversalCommand {
    private String requestId;
    private String reason;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
