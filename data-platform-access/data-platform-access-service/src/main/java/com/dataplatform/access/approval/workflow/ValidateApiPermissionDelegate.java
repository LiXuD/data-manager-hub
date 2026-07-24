package com.dataplatform.access.approval.workflow;

import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApplicationStatus;
import com.dataplatform.access.approval.service.ApiPermissionApplicationService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("validateApiPermissionDelegate")
public class ValidateApiPermissionDelegate implements JavaDelegate {

    private final ApiPermissionApplicationService applicationService;

    public ValidateApiPermissionDelegate(ApiPermissionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long applicationId = numberVariable(execution, "applicationId");
        ApiPermissionApplication application = applicationService.requireApplication(applicationId);
        if (!ApplicationStatus.IN_REVIEW.name().equals(application.getStatus())) {
            throw new IllegalStateException("申请状态不是 IN_REVIEW");
        }
        if (!application.getTenantId().toString().equals(execution.getTenantId())) {
            throw new IllegalStateException("流程实例租户与申请租户不一致");
        }
    }

    private Long numberVariable(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("流程变量缺失: " + name);
        }
        return number.longValue();
    }
}
