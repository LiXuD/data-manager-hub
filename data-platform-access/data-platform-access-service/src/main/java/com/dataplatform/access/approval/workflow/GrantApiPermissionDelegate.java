package com.dataplatform.access.approval.workflow;

import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApiPermissionApplicationItem;
import com.dataplatform.access.approval.domain.ApplicationStatus;
import com.dataplatform.access.approval.domain.EngineStatus;
import com.dataplatform.access.approval.domain.GrantSource;
import com.dataplatform.access.approval.service.ApiPermissionApplicationService;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component("grantApiPermissionDelegate")
public class GrantApiPermissionDelegate implements JavaDelegate {

    private final ApiPermissionApplicationService applicationService;
    private final ApiKeyInterfaceService grantService;

    public GrantApiPermissionDelegate(
            ApiPermissionApplicationService applicationService,
            ApiKeyInterfaceService grantService) {
        this.applicationService = applicationService;
        this.grantService = grantService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long applicationId = numberVariable(execution, "applicationId");
        ApiPermissionApplication application = applicationService.requireApplication(applicationId);
        if (!ApplicationStatus.IN_REVIEW.name().equals(application.getStatus())) {
            throw new IllegalStateException("申请状态已变化，不能开通权限");
        }
        applicationService.validateProvisioningResources(application);

        application.setStatus(ApplicationStatus.PROVISIONING.name());
        applicationService.updateApplication(application);
        applicationService.updateItemStatus(applicationId, ApplicationStatus.PROVISIONING);

        Long approverUserId = optionalNumberVariable(execution, "approverUserId");
        for (ApiPermissionApplicationItem item : applicationService.listItems(applicationId)) {
            ApiKeyInterface grant = grantService.grant(
                    application.getApiKeyId(),
                    item.getInterfaceId(),
                    GrantSource.APPROVAL,
                    item.getId(),
                    application.getApprovedExpireAt(),
                    approverUserId,
                    Boolean.TRUE.equals(item.getApprovedCacheEnabled()),
                    item.getApprovedCacheDays());
            item.setGrantId(grant.getId());
            item.setItemStatus(ApplicationStatus.EFFECTIVE.name());
            applicationService.updateItem(item);
        }

        application.setStatus(ApplicationStatus.EFFECTIVE.name());
        application.setEngineStatus(EngineStatus.COMPLETED.name());
        application.setCurrentTaskId(null);
        application.setCurrentTaskKey(null);
        application.setCurrentTaskName(null);
        application.setCurrentTaskCreatedAt(null);
        if (application.getDecidedAt() == null) {
            application.setDecidedAt(LocalDateTime.now());
        }
        applicationService.updateApplication(application);
        applicationService.appendAction(
                application,
                "GRANT",
                "SYSTEM",
                null,
                "workflow",
                ApplicationStatus.PROVISIONING.name(),
                ApplicationStatus.EFFECTIVE.name(),
                "审批通过，接口权限已开通",
                null);
    }

    private Long numberVariable(DelegateExecution execution, String name) {
        Long value = optionalNumberVariable(execution, name);
        if (value == null) {
            throw new IllegalStateException("流程变量缺失: " + name);
        }
        return value;
    }

    private Long optionalNumberVariable(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        return value instanceof Number number ? number.longValue() : null;
    }
}
