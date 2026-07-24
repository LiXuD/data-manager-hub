package com.dataplatform.access.approval.api;

import com.dataplatform.access.approval.engine.ApprovalEnginePort;

public record ApprovalTaskDetailResponse(
        ApprovalEnginePort.TaskSnapshot task,
        ApprovalEnginePort.TaskPolicy policy,
        ApplicationDetailResponse application) {
}
