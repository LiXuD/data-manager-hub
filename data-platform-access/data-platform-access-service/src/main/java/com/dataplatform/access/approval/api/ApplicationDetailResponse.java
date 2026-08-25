package com.dataplatform.access.approval.api;

import com.dataplatform.access.approval.domain.ApiPermissionAction;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApiPermissionApplicationItem;

import java.util.List;

public record ApplicationDetailResponse(
        ApiPermissionApplication application,
        List<ApiPermissionApplicationItem> items,
        List<ApiPermissionAction> actions) {
}
