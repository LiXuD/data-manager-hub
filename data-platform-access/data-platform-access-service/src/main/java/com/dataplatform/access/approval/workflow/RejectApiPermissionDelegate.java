package com.dataplatform.access.approval.workflow;

import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApplicationStatus;
import com.dataplatform.access.approval.domain.EngineStatus;
import com.dataplatform.access.approval.service.ApiPermissionApplicationService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("rejectApiPermissionDelegate")
public class RejectApiPermissionDelegate implements JavaDelegate {

    private final ApiPermissionApplicationService applicationService;

    public RejectApiPermissionDelegate(ApiPermissionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long applicationId = numberVariable(execution, "applicationId");
        ApiPermissionApplication application = applicationService.requireApplication(applicationId);
        if (!ApplicationStatus.IN_REVIEW.name().equals(application.getStatus())) {
            throw new IllegalStateException("申请状态已变化，不能驳回");
        }
        application.setStatus(ApplicationStatus.REJECTED.name());
        application.setEngineStatus(EngineStatus.COMPLETED.name());
        application.setApprovedExpireAt(null);
        application.setCurrentTaskId(null);
        application.setCurrentTaskKey(null);
        application.setCurrentTaskName(null);
        application.setCurrentTaskCreatedAt(null);
        applicationService.updateApplication(application);
        applicationService.updateItemStatus(applicationId, ApplicationStatus.REJECTED);
    }

    private Long numberVariable(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("流程变量缺失: " + name);
        }
        return number.longValue();
    }
}
