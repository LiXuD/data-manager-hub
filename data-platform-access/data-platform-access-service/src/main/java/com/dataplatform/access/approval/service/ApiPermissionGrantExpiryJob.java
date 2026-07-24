package com.dataplatform.access.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApiPermissionAction;
import com.dataplatform.access.approval.domain.ApiPermissionApplicationItem;
import com.dataplatform.access.approval.domain.ApplicationStatus;
import com.dataplatform.access.approval.domain.GrantStatus;
import com.dataplatform.access.approval.mapper.ApiPermissionActionMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationItemMapper;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ApiPermissionGrantExpiryJob {

    private final ApiKeyInterfaceService grantService;
    private final ApiPermissionApplicationItemMapper itemMapper;
    private final ApiPermissionActionMapper actionMapper;
    private final ApiPermissionApplicationService applicationService;

    public ApiPermissionGrantExpiryJob(
            ApiKeyInterfaceService grantService,
            ApiPermissionApplicationItemMapper itemMapper,
            ApiPermissionActionMapper actionMapper,
            ApiPermissionApplicationService applicationService) {
        this.grantService = grantService;
        this.itemMapper = itemMapper;
        this.actionMapper = actionMapper;
        this.applicationService = applicationService;
    }

    @Scheduled(
            initialDelayString = "${api-permission.expiry.initial-delay-ms:60000}",
            fixedDelayString = "${api-permission.expiry.fixed-delay-ms:60000}")
    @Transactional
    public void expireDueGrants() {
        LocalDateTime now = LocalDateTime.now();
        List<ApiKeyInterface> dueGrants = grantService.list(
                new LambdaQueryWrapper<ApiKeyInterface>()
                        .eq(ApiKeyInterface::getStatus, GrantStatus.ACTIVE.name())
                        .isNotNull(ApiKeyInterface::getExpireAt)
                        .le(ApiKeyInterface::getExpireAt, now)
                        .last("LIMIT 500"));
        for (ApiKeyInterface grant : dueGrants) {
            grant.setStatus(GrantStatus.EXPIRED.name());
            grant.setUpdatedAt(now);
            if (!grantService.updateById(grant)) {
                continue;
            }
            if (grant.getApplicationItemId() == null) {
                appendDetachedExpiryAction(grant, now);
                continue;
            }
            ApiPermissionApplicationItem item =
                    itemMapper.selectById(grant.getApplicationItemId());
            if (item == null
                    || ApplicationStatus.EXPIRED.name().equals(item.getItemStatus())) {
                continue;
            }
            item.setItemStatus(ApplicationStatus.EXPIRED.name());
            applicationService.updateItem(item);

            ApiPermissionApplication application =
                    applicationService.requireApplication(item.getApplicationId());
            boolean anyEffective = applicationService.listItems(application.getId()).stream()
                    .anyMatch(candidate ->
                            ApplicationStatus.EFFECTIVE.name().equals(candidate.getItemStatus()));
            if (!anyEffective) {
                String previous = application.getStatus();
                application.setStatus(ApplicationStatus.EXPIRED.name());
                applicationService.updateApplication(application);
                applicationService.appendAction(
                        application,
                        "EXPIRE",
                        "SYSTEM",
                        null,
                        "expiry-job",
                        previous,
                        ApplicationStatus.EXPIRED.name(),
                        "接口授权已到期",
                        null);
            }
        }
    }

    private void appendDetachedExpiryAction(ApiKeyInterface grant, LocalDateTime now) {
        ApiPermissionAction action = new ApiPermissionAction();
        action.setAction("EXPIRE");
        action.setActorType("SYSTEM");
        action.setActorNameSnapshot("expiry-job");
        action.setFromStatus(GrantStatus.ACTIVE.name());
        action.setToStatus(GrantStatus.EXPIRED.name());
        action.setComment("授权已到期 grantId=" + grant.getId()
                + "，source=" + grant.getGrantSource());
        action.setCreatedAt(now);
        actionMapper.insert(action);
    }
}
