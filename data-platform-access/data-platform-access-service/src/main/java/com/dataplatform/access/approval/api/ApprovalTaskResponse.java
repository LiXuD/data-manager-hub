package com.dataplatform.access.approval.api;

import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.engine.ApprovalEnginePort;

public record ApprovalTaskResponse(
        ApprovalEnginePort.TaskSnapshot task,
        ApiPermissionApplication application) {
}
