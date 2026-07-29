package com.dataplatform.access.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.ApiKeyOptionResponse;
import com.dataplatform.access.approval.api.ApplicationDetailResponse;
import com.dataplatform.access.approval.api.ApplicationUpsertRequest;
import com.dataplatform.access.approval.api.CallerOptionResponse;
import com.dataplatform.access.approval.api.InterfaceOptionResponse;
import com.dataplatform.access.approval.domain.ApiPermissionAction;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApiPermissionApplicationItem;
import com.dataplatform.access.approval.domain.ApplicationStatus;
import com.dataplatform.access.approval.domain.ApprovalProcessConfig;
import com.dataplatform.access.approval.domain.EngineStatus;
import com.dataplatform.access.approval.engine.ApprovalEnginePort;
import com.dataplatform.access.approval.mapper.ApiPermissionActionMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationItemMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationMapper;
import com.dataplatform.access.approval.mapper.ApprovalProcessConfigMapper;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.security.RoleCodeNormalizer;
import com.dataplatform.identity.api.dto.CallerAccessDTO;
import com.dataplatform.identity.api.feign.IdentityAccessInternalFeignClient;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApiPermissionApplicationService {

    private static final int MAX_INTERFACES = 100;
    private static final int MAX_CACHE_DAYS = 365;
    private static final long MAX_EXPECTED_DAILY_CALLS = 100_000_000L;
    private static final DateTimeFormatter APPLICATION_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ApiPermissionApplicationMapper applicationMapper;
    private final ApiPermissionApplicationItemMapper itemMapper;
    private final ApiPermissionActionMapper actionMapper;
    private final ApprovalProcessConfigMapper processConfigMapper;
    private final CallerService callerService;
    private final ApiKeyService apiKeyService;
    private final ApiKeyInterfaceService grantService;
    private final IdentityAccessInternalFeignClient identityClient;
    private final ApiInterfaceFeignClient interfaceClient;
    private final ApprovalEnginePort approvalEngine;
    private final TransactionTemplate transactionTemplate;

    public ApiPermissionApplicationService(
            ApiPermissionApplicationMapper applicationMapper,
            ApiPermissionApplicationItemMapper itemMapper,
            ApiPermissionActionMapper actionMapper,
            ApprovalProcessConfigMapper processConfigMapper,
            CallerService callerService,
            ApiKeyService apiKeyService,
            ApiKeyInterfaceService grantService,
            IdentityAccessInternalFeignClient identityClient,
            ApiInterfaceFeignClient interfaceClient,
            ApprovalEnginePort approvalEngine,
            PlatformTransactionManager transactionManager) {
        this.applicationMapper = applicationMapper;
        this.itemMapper = itemMapper;
        this.actionMapper = actionMapper;
        this.processConfigMapper = processConfigMapper;
        this.callerService = callerService;
        this.apiKeyService = apiKeyService;
        this.grantService = grantService;
        this.identityClient = identityClient;
        this.interfaceClient = interfaceClient;
        this.approvalEngine = approvalEngine;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ApiPermissionApplication createDraft(
            ApplicationUpsertRequest request,
            Long userId,
            String username,
            Long tenantId,
            boolean tenantWideCallerAccess) {
        ValidatedResources resources = validateResources(
                request, userId, tenantId, false, tenantWideCallerAccess);
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            ApiPermissionApplication application = new ApiPermissionApplication();
            application.setApplicationNo(generateApplicationNo());
            applyRequest(application, request, resources, userId, username, tenantId);
            application.setStatus(ApplicationStatus.DRAFT.name());
            application.setEngineType("FLOWABLE");
            application.setEngineStatus(EngineStatus.NOT_STARTED.name());
            application.setVersion(0);
            application.setCreatedAt(now);
            application.setUpdatedAt(now);
            applicationMapper.insert(application);
            replaceItems(application, resources.interfaces(), ApplicationStatus.DRAFT, request);
            appendAction(application, "CREATE", "USER", userId, username,
                    null, ApplicationStatus.DRAFT.name(), null, null);
            return application;
        });
    }

    public ApiPermissionApplication updateDraft(
            Long id,
            ApplicationUpsertRequest request,
            Long userId,
            String username,
            Long tenantId,
            boolean tenantWideCallerAccess) {
        ValidatedResources resources = validateResources(
                request, userId, tenantId, false, tenantWideCallerAccess);
        return transactionTemplate.execute(status -> {
            ApiPermissionApplication application = requireOwnedApplication(id, userId);
            requireStatus(application, ApplicationStatus.DRAFT);
            applyRequest(application, request, resources, userId, username, tenantId);
            updateApplication(application);
            replaceItems(application, resources.interfaces(), ApplicationStatus.DRAFT, request);
            return application;
        });
    }

    public ApiPermissionApplication submit(
            Long id,
            String idempotencyKey,
            Long userId,
            String username,
            Long tenantId,
            boolean tenantWideCallerAccess) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw badRequest("IDEMPOTENCY_KEY_REQUIRED", "提交申请必须携带有效 Idempotency-Key");
        }
        String normalizedIdempotencyKey = idempotencyKey.trim();
        ApiPermissionApplication replay = findIdempotentReplay(userId, normalizedIdempotencyKey);
        if (replay != null) {
            return requireSameIdempotentRequest(id, replay);
        }

        try {
            return transactionTemplate.execute(status -> {
                ApiPermissionApplication locked = requireOwnedApplicationForUpdate(id, userId);
                ApiPermissionApplication concurrentReplay = findIdempotentReplay(
                        userId, normalizedIdempotencyKey);
                if (concurrentReplay != null) {
                    return requireSameIdempotentRequest(id, concurrentReplay);
                }
                requireStatus(locked, ApplicationStatus.DRAFT);
                List<ApiPermissionApplicationItem> items = listItems(id);
                ApplicationUpsertRequest request = toRequest(locked, items);
                ValidatedResources resources = validateResources(
                        request, userId, tenantId, true, tenantWideCallerAccess);
                validateNoPendingOrEffectiveGrant(locked, resources.interfaces());
                ApprovalProcessConfig processConfig = resolveProcessConfig(
                        tenantId, "API_PERMISSION_" + locked.getRequestType(), riskLevel(locked));

                LocalDateTime now = LocalDateTime.now();
                locked.setStatus(ApplicationStatus.IN_REVIEW.name());
                locked.setEngineStatus(EngineStatus.RUNNING.name());
                locked.setIdempotencyKey(normalizedIdempotencyKey);
                locked.setSubmittedAt(now);
                locked.setProcessDefinitionKey(processConfig.getProcessDefinitionKey());
                updateApplication(locked);
                updateItemStatus(locked.getId(), ApplicationStatus.IN_REVIEW);

                Map<String, Object> variables = new HashMap<>();
                variables.put("applicationId", locked.getId());
                variables.put("applicationNo", locked.getApplicationNo());
                variables.put("applicantUserId", locked.getApplicantUserId());
                variables.put("tenantId", locked.getTenantId());
                variables.put("callerId", locked.getCallerId());
                variables.put("apiKeyId", locked.getApiKeyId());
                variables.put("requestType", locked.getRequestType());
                variables.put("riskLevel", riskLevel(locked));
                variables.put("expectedDailyCalls", locked.getExpectedDailyCalls());
                variables.put("requestedExpireAt", locked.getRequestedExpireAt().toString());
                ApiPermissionApplicationItem firstItem = items.get(0);
                variables.put("requestedCacheEnabled",
                        Boolean.TRUE.equals(firstItem.getRequestedCacheEnabled()));
                variables.put("requestedCacheDays", firstItem.getRequestedCacheDays());
                String approverGroup = RoleCodeNormalizer.normalize(
                        processConfig.getApproverGroup());
                if (approverGroup == null) {
                    throw conflict(
                            "APPROVAL_GROUP_NOT_CONFIGURED",
                            "审批流程未配置有效候选角色");
                }
                variables.put("approverGroup", approverGroup);

                ApprovalEnginePort.StartResult start = approvalEngine.start(
                        processConfig.getProcessDefinitionKey(),
                        locked.getApplicationNo(),
                        locked.getTenantId(),
                        variables);
                locked.setProcessInstanceId(start.processInstanceId());
                locked.setProcessDefinitionVersion(start.processDefinitionVersion());
                projectCurrentTaskInternal(locked, start.currentTask());
                updateApplication(locked);
                appendAction(locked, "SUBMIT", "USER", userId, username,
                        ApplicationStatus.DRAFT.name(), ApplicationStatus.IN_REVIEW.name(),
                        null, start.currentTask());
                return locked;
            });
        } catch (DuplicateKeyException exception) {
            ApiPermissionApplication concurrentReplay = findIdempotentReplay(
                    userId, normalizedIdempotencyKey);
            if (concurrentReplay != null) {
                return requireSameIdempotentRequest(id, concurrentReplay);
            }
            throw conflict("DUPLICATE_PENDING_APPLICATION", "相同 API Key 和接口已有待审批申请");
        }
    }

    @Transactional
    public ApiPermissionApplication cancel(Long id, Long userId, String username) {
        ApiPermissionApplication application = requireOwnedApplication(id, userId);
        ApplicationStatus current = ApplicationStatus.valueOf(application.getStatus());
        if (current != ApplicationStatus.DRAFT && current != ApplicationStatus.IN_REVIEW) {
            throw conflict("APPLICATION_STATE_CONFLICT", "当前状态不允许取消");
        }
        if (current == ApplicationStatus.IN_REVIEW) {
            List<ApprovalEnginePort.TaskSnapshot> activeTasks = approvalEngine
                    .getCurrentTasks(application.getProcessInstanceId());
            if (activeTasks.isEmpty()
                    || activeTasks.stream()
                    .map(task -> approvalEngine.getTaskPolicy(task.id()))
                    .anyMatch(policy -> !policy.allowWithdraw())) {
                throw conflict("WITHDRAWAL_NOT_ALLOWED", "当前审批节点不允许申请人撤回");
            }
            approvalEngine.terminate(application.getProcessInstanceId(), "申请人撤回");
            application.setEngineStatus(EngineStatus.TERMINATED.name());
        }
        application.setStatus(ApplicationStatus.CANCELED.name());
        application.setCurrentTaskId(null);
        application.setCurrentTaskKey(null);
        application.setCurrentTaskName(null);
        application.setCurrentTaskCreatedAt(null);
        updateApplication(application);
        updateItemStatus(id, ApplicationStatus.CANCELED);
        appendAction(application, "CANCEL", "USER", userId, username,
                current.name(), ApplicationStatus.CANCELED.name(), null, null);
        return application;
    }

    public ApiPermissionApplication copy(
            Long id,
            Long userId,
            String username,
            Long tenantId,
            boolean tenantWideCallerAccess) {
        requireOwnedApplication(id, userId);
        ApplicationDetailResponse source = detail(id);
        ApplicationUpsertRequest request = toRequest(source.application(), source.items());
        return createDraft(request, userId, username, tenantId, tenantWideCallerAccess);
    }

    public PageResult<ApiPermissionApplication> list(
            Long userId,
            Long tenantId,
            boolean tenantScope,
            String applicationStatus,
            int page,
            int pageSize) {
        LambdaQueryWrapper<ApiPermissionApplication> query = new LambdaQueryWrapper<>();
        if (tenantScope) {
            query.eq(ApiPermissionApplication::getTenantId, tenantId);
        } else {
            query.eq(ApiPermissionApplication::getApplicantUserId, userId);
        }
        if (applicationStatus != null && !applicationStatus.isBlank()) {
            query.eq(ApiPermissionApplication::getStatus, applicationStatus);
        }
        query.orderByDesc(ApiPermissionApplication::getCreatedAt);
        Page<ApiPermissionApplication> result = applicationMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100)), query);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    public ApplicationDetailResponse detail(Long id) {
        ApiPermissionApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw notFound("APPLICATION_NOT_FOUND", "申请不存在");
        }
        List<ApiPermissionAction> actions = actionMapper.selectList(
                new LambdaQueryWrapper<ApiPermissionAction>()
                        .eq(ApiPermissionAction::getApplicationId, id)
                        .orderByAsc(ApiPermissionAction::getCreatedAt));
        return new ApplicationDetailResponse(application, listItems(id), actions);
    }

    public List<CallerOptionResponse> eligibleCallers(
            Long userId,
            Long tenantId,
            boolean tenantWideCallerAccess) {
        if (tenantWideCallerAccess) {
            return callerService.listByTenant(tenantId)
                    .stream()
                    .map(caller -> new CallerOptionResponse(
                            caller.getId(), caller.getCallerCode(), caller.getCallerName()))
                    .toList();
        }
        List<Long> callerIds = requireData(identityClient.getCallerIds(userId), "身份服务返回异常");
        if (callerIds.isEmpty()) {
            return List.of();
        }
        return callerService.listByIds(callerIds).stream()
                .filter(caller -> tenantId.equals(caller.getTenantId()))
                .filter(caller -> CommonStatus.ACTIVE.equals(caller.getStatus()))
                .map(caller -> new CallerOptionResponse(
                        caller.getId(), caller.getCallerCode(), caller.getCallerName()))
                .toList();
    }

    public List<ApiKeyOptionResponse> callerApiKeys(
            Long callerId,
            Long userId,
            Long tenantId,
            boolean tenantWideCallerAccess) {
        validateCallerAccess(callerId, userId, tenantId, tenantWideCallerAccess);
        LocalDateTime now = LocalDateTime.now();
        return apiKeyService.listByCaller(callerId).stream()
                .filter(key -> ApiKeyStatus.ACTIVE.equals(key.getStatus()))
                .filter(key -> key.getExpireTime() == null || key.getExpireTime().isAfter(now))
                .map(key -> new ApiKeyOptionResponse(
                        key.getId(),
                        key.getCallerId(),
                        key.getKeyName(),
                        key.getStatus().getCode(),
                        key.getExpireTime()))
                .toList();
    }

    public List<InterfaceOptionResponse> interfaceOptions(
            Long apiKeyId,
            String keyword,
            Long userId,
            Long tenantId,
            boolean tenantWideCallerAccess) {
        ApiKey apiKey = requireApiKey(apiKeyId);
        validateCallerAccess(
                apiKey.getCallerId(), userId, tenantId, tenantWideCallerAccess);
        List<ApiInterfaceDTO> options = requireData(interfaceClient.getOptions(keyword), "主数据服务返回异常");
        Set<Long> granted = new HashSet<>(grantService.getInterfaceIdsByApiKeyId(apiKeyId));
        Set<Long> pending = itemMapper.selectList(
                        new LambdaQueryWrapper<ApiPermissionApplicationItem>()
                                .eq(ApiPermissionApplicationItem::getApiKeyId, apiKeyId)
                                .in(ApiPermissionApplicationItem::getItemStatus,
                                        ApplicationStatus.IN_REVIEW.name(),
                                        ApplicationStatus.PROVISIONING.name()))
                .stream()
                .map(ApiPermissionApplicationItem::getInterfaceId)
                .collect(Collectors.toSet());
        return options.stream()
                .map(option -> new InterfaceOptionResponse(
                        option.getId(),
                        option.getInterfaceCode(),
                        option.getInterfaceName(),
                        option.getStatus(),
                        granted.contains(option.getId()),
                        pending.contains(option.getId())))
                .toList();
    }

    public ApiPermissionApplication findByProcessInstance(String processInstanceId) {
        return applicationMapper.selectOne(new LambdaQueryWrapper<ApiPermissionApplication>()
                .eq(ApiPermissionApplication::getProcessInstanceId, processInstanceId));
    }

    public List<ApiPermissionApplicationItem> listItems(Long applicationId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ApiPermissionApplicationItem>()
                .eq(ApiPermissionApplicationItem::getApplicationId, applicationId)
                .orderByAsc(ApiPermissionApplicationItem::getId));
    }

    public ApiPermissionApplication requireApplication(Long id) {
        ApiPermissionApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw notFound("APPLICATION_NOT_FOUND", "申请不存在");
        }
        return application;
    }

    public ApiPermissionApplication requireVisibleApplication(
            Long id,
            Long userId,
            Long tenantId,
            boolean tenantScope) {
        ApiPermissionApplication application = requireApplication(id);
        boolean visible = tenantScope
                ? tenantId.equals(application.getTenantId())
                : userId.equals(application.getApplicantUserId());
        if (!visible) {
            throw forbidden("APPLICATION_ACCESS_DENIED", "无权查看该申请");
        }
        return application;
    }

    public List<ApiInterfaceDTO> validateProvisioningResources(
            ApiPermissionApplication application) {
        CallerInfo caller = callerService.getById(application.getCallerId());
        if (caller == null
                || !application.getTenantId().equals(caller.getTenantId())
                || !CommonStatus.ACTIVE.equals(caller.getStatus())) {
            throw conflict("CALLER_NOT_ACTIVE", "Caller 已停用或租户信息不一致");
        }
        ApiKey apiKey = requireApiKey(application.getApiKeyId());
        if (!application.getCallerId().equals(apiKey.getCallerId())
                || !ApiKeyStatus.ACTIVE.equals(apiKey.getStatus())
                || (apiKey.getExpireTime() != null
                && !apiKey.getExpireTime().isAfter(LocalDateTime.now()))) {
            throw conflict("API_KEY_NOT_ACTIVE", "API Key 已停用、过期或不属于所选 Caller");
        }
        List<Long> interfaceIds = listItems(application.getId()).stream()
                .map(ApiPermissionApplicationItem::getInterfaceId)
                .toList();
        List<ApiInterfaceDTO> interfaces = requireData(
                interfaceClient.batchGet(interfaceIds), "主数据服务返回异常");
        Set<Long> returnedIds = interfaces.stream()
                .map(ApiInterfaceDTO::getId)
                .collect(Collectors.toSet());
        if (!returnedIds.containsAll(interfaceIds)
                || interfaces.stream().anyMatch(item -> !"active".equalsIgnoreCase(item.getStatus()))) {
            throw conflict("INTERFACE_NOT_ACTIVE", "部分接口不存在或已停用");
        }
        return interfaces;
    }

    public void projectCurrentTask(
            ApiPermissionApplication application,
            ApprovalEnginePort.TaskSnapshot task) {
        projectCurrentTaskInternal(application, task);
    }

    public void updateApplication(ApiPermissionApplication application) {
        application.setUpdatedAt(LocalDateTime.now());
        if (applicationMapper.updateById(application) != 1) {
            throw conflict("APPLICATION_VERSION_CONFLICT", "申请已被并发更新，请刷新后重试");
        }
    }

    public void updateItem(ApiPermissionApplicationItem item) {
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    public void updateItemStatus(Long applicationId, ApplicationStatus status) {
        List<ApiPermissionApplicationItem> items = listItems(applicationId);
        for (ApiPermissionApplicationItem item : items) {
            item.setItemStatus(status.name());
            item.setUpdatedAt(LocalDateTime.now());
            itemMapper.updateById(item);
        }
    }

    public void applyApprovedCachePolicy(
            Long applicationId,
            boolean cacheEnabled,
            Integer approvedCacheDays) {
        for (ApiPermissionApplicationItem item : listItems(applicationId)) {
            item.setApprovedCacheEnabled(cacheEnabled);
            item.setApprovedCacheDays(cacheEnabled ? approvedCacheDays : null);
            updateItem(item);
        }
    }

    public void appendAction(
            ApiPermissionApplication application,
            String action,
            String actorType,
            Long actorUserId,
            String actorName,
            String fromStatus,
            String toStatus,
            String comment,
            ApprovalEnginePort.TaskSnapshot task) {
        ApiPermissionAction record = new ApiPermissionAction();
        record.setApplicationId(application.getId());
        record.setAction(action);
        record.setActorType(actorType);
        record.setActorUserId(actorUserId);
        record.setActorNameSnapshot(actorName);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setComment(comment);
        record.setEngineType(application.getEngineType());
        record.setProcessInstanceId(application.getProcessInstanceId());
        if (task != null) {
            record.setTaskId(task.id());
            record.setTaskDefinitionKey(task.taskDefinitionKey());
            record.setTaskName(task.name());
            record.setTaskAssignee(task.assignee());
        }
        record.setProcessDefinitionVersion(application.getProcessDefinitionVersion());
        record.setTraceId(MDC.get("traceId"));
        record.setCreatedAt(LocalDateTime.now());
        actionMapper.insert(record);
    }

    private ValidatedResources validateResources(
            ApplicationUpsertRequest request,
            Long userId,
            Long tenantId,
            boolean submitting,
            boolean tenantWideCallerAccess) {
        validateRequest(request, submitting);
        CallerInfo caller = validateCallerAccess(
                request.callerId(), userId, tenantId, tenantWideCallerAccess);
        ApiKey apiKey = requireApiKey(request.apiKeyId());
        if (!caller.getId().equals(apiKey.getCallerId())) {
            throw badRequest("API_KEY_CALLER_MISMATCH", "API Key 不属于所选 Caller");
        }
        if (submitting && (!ApiKeyStatus.ACTIVE.equals(apiKey.getStatus())
                || (apiKey.getExpireTime() != null && !apiKey.getExpireTime().isAfter(LocalDateTime.now())))) {
            throw conflict("API_KEY_NOT_ACTIVE", "API Key 已停用或过期");
        }

        List<Long> interfaceIds = request.interfaceIds().stream().distinct().toList();
        List<ApiInterfaceDTO> interfaces = requireData(
                interfaceClient.batchGet(interfaceIds), "主数据服务返回异常");
        Map<Long, ApiInterfaceDTO> byId = interfaces.stream()
                .collect(Collectors.toMap(ApiInterfaceDTO::getId, Function.identity()));
        if (byId.size() != interfaceIds.size()) {
            throw notFound("INTERFACE_NOT_FOUND", "部分接口不存在");
        }
        if (submitting && interfaces.stream().anyMatch(item -> !"active".equalsIgnoreCase(item.getStatus()))) {
            throw conflict("INTERFACE_NOT_ACTIVE", "部分接口已停用");
        }
        return new ValidatedResources(
                caller,
                apiKey,
                interfaceIds.stream().map(byId::get).toList());
    }

    private CallerInfo validateCallerAccess(
            Long callerId,
            Long userId,
            Long tenantId,
            boolean tenantWideCallerAccess) {
        CallerInfo caller = callerService.getById(callerId);
        if (caller == null) {
            throw notFound("CALLER_NOT_FOUND", "Caller 不存在");
        }
        if (!tenantId.equals(caller.getTenantId()) || !CommonStatus.ACTIVE.equals(caller.getStatus())) {
            throw forbidden("CALLER_ACCESS_DENIED", "Caller 不属于当前租户或已停用");
        }
        if (tenantWideCallerAccess) {
            return caller;
        }
        CallerAccessDTO access = requireData(
                identityClient.getCallerAccess(userId, callerId), "身份服务返回异常");
        if (!access.isAllowed() || !tenantId.equals(access.getTenantId())) {
            throw forbidden("CALLER_ACCESS_DENIED", "当前用户无权管理该 Caller");
        }
        return caller;
    }

    private ApiKey requireApiKey(Long apiKeyId) {
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        if (apiKey == null) {
            throw notFound("API_KEY_NOT_FOUND", "API Key 不存在");
        }
        return apiKey;
    }

    private void validateRequest(ApplicationUpsertRequest request, boolean submitting) {
        if (request == null) {
            throw badRequest("INVALID_REQUEST", "请求体不能为空");
        }
        if (!Set.of("OPEN", "RENEW").contains(request.requestType())) {
            throw badRequest("INVALID_REQUEST_TYPE", "requestType 只能是 OPEN 或 RENEW");
        }
        if (request.callerId() == null || request.apiKeyId() == null) {
            throw badRequest("INVALID_RESOURCE", "Caller 和 API Key 不能为空");
        }
        if (request.interfaceIds() == null
                || request.interfaceIds().isEmpty()
                || request.interfaceIds().size() > MAX_INTERFACES
                || request.interfaceIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw badRequest("INVALID_INTERFACES", "接口数量必须在 1 到 100 之间");
        }
        if (request.businessPurpose() == null
                || request.businessPurpose().trim().length() < 10
                || request.businessPurpose().trim().length() > 1000) {
            throw badRequest("INVALID_BUSINESS_PURPOSE", "业务用途长度必须在 10 到 1000 字之间");
        }
        if (request.businessScene() == null
                || request.businessScene().isBlank()
                || request.businessScene().length() > 200) {
            throw badRequest("INVALID_BUSINESS_SCENE", "业务场景不能为空且不能超过 200 字");
        }
        if (request.expectedDailyCalls() == null
                || request.expectedDailyCalls() <= 0
                || request.expectedDailyCalls() > MAX_EXPECTED_DAILY_CALLS) {
            throw badRequest("INVALID_DAILY_CALLS", "预计日调用量必须在 1 到 100000000 之间");
        }
        if (submitting && (request.requestedExpireAt() == null
                || !request.requestedExpireAt().isAfter(LocalDateTime.now()))) {
            throw badRequest("INVALID_EXPIRY", "申请有效截止时间必须晚于当前时间");
        }
        boolean cacheEnabled = Boolean.TRUE.equals(request.cacheEnabled());
        if (cacheEnabled && (request.requestedCacheDays() == null
                || request.requestedCacheDays() < 1
                || request.requestedCacheDays() > MAX_CACHE_DAYS)) {
            throw badRequest(
                    "INVALID_CACHE_POLICY",
                    "申请使用缓存时，缓存时效必须在 1 到 365 天之间");
        }
        if (!cacheEnabled && request.requestedCacheDays() != null) {
            throw badRequest(
                    "INVALID_CACHE_POLICY",
                    "未申请缓存时不能填写缓存时效");
        }
    }

    private void validateNoPendingOrEffectiveGrant(
            ApiPermissionApplication application,
            List<ApiInterfaceDTO> interfaces) {
        for (ApiInterfaceDTO apiInterface : interfaces) {
            long pending = itemMapper.selectCount(new LambdaQueryWrapper<ApiPermissionApplicationItem>()
                    .eq(ApiPermissionApplicationItem::getApiKeyId, application.getApiKeyId())
                    .eq(ApiPermissionApplicationItem::getInterfaceId, apiInterface.getId())
                    .ne(ApiPermissionApplicationItem::getApplicationId, application.getId())
                    .in(ApiPermissionApplicationItem::getItemStatus,
                            ApplicationStatus.IN_REVIEW.name(),
                            ApplicationStatus.PROVISIONING.name()));
            if (pending > 0) {
                throw conflict("DUPLICATE_PENDING_APPLICATION", "相同 API Key 和接口已有待审批申请");
            }
            boolean active = grantService.hasInterfacePermission(application.getApiKeyId(), apiInterface.getId());
            if ("OPEN".equals(application.getRequestType()) && active) {
                throw conflict("GRANT_ALREADY_ACTIVE", "接口已拥有有效授权，请使用续期申请");
            }
            if ("RENEW".equals(application.getRequestType()) && !active) {
                throw conflict("GRANT_NOT_ACTIVE", "续期申请要求接口当前已有有效授权");
            }
        }
    }

    private ApprovalProcessConfig resolveProcessConfig(
            Long tenantId,
            String businessType,
            String riskLevel) {
        List<ApprovalProcessConfig> configs = processConfigMapper.selectList(
                new LambdaQueryWrapper<ApprovalProcessConfig>()
                        .in(ApprovalProcessConfig::getTenantId, tenantId, 0L)
                        .eq(ApprovalProcessConfig::getBusinessType, businessType)
                        .eq(ApprovalProcessConfig::getEngineType, "FLOWABLE")
                        .in(ApprovalProcessConfig::getRiskLevel, riskLevel, "*")
                        .eq(ApprovalProcessConfig::getEnabled, true));
        return configs.stream()
                .sorted(Comparator
                        .comparing((ApprovalProcessConfig config) -> config.getTenantId().equals(tenantId))
                        .reversed()
                        .thenComparing(config -> config.getRiskLevel().equals(riskLevel), Comparator.reverseOrder())
                        .thenComparing(ApprovalProcessConfig::getPriority, Comparator.reverseOrder()))
                .findFirst()
                .orElseThrow(() -> conflict(
                        "APPROVAL_PROCESS_NOT_CONFIGURED", "未配置可用的审批流程"));
    }

    private void replaceItems(
            ApiPermissionApplication application,
            List<ApiInterfaceDTO> interfaces,
            ApplicationStatus status,
            ApplicationUpsertRequest request) {
        itemMapper.delete(new LambdaQueryWrapper<ApiPermissionApplicationItem>()
                .eq(ApiPermissionApplicationItem::getApplicationId, application.getId()));
        LocalDateTime now = LocalDateTime.now();
        for (ApiInterfaceDTO apiInterface : interfaces) {
            ApiPermissionApplicationItem item = new ApiPermissionApplicationItem();
            item.setApplicationId(application.getId());
            item.setApiKeyId(application.getApiKeyId());
            item.setInterfaceId(apiInterface.getId());
            item.setInterfaceCodeSnapshot(apiInterface.getInterfaceCode());
            item.setInterfaceNameSnapshot(apiInterface.getInterfaceName());
            item.setInterfaceStatusSnapshot(apiInterface.getStatus());
            item.setItemStatus(status.name());
            item.setRequestedCacheEnabled(Boolean.TRUE.equals(request.cacheEnabled()));
            item.setRequestedCacheDays(request.requestedCacheDays());
            item.setApprovedCacheEnabled(false);
            item.setApprovedCacheDays(null);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            itemMapper.insert(item);
        }
    }

    private void applyRequest(
            ApiPermissionApplication application,
            ApplicationUpsertRequest request,
            ValidatedResources resources,
            Long userId,
            String username,
            Long tenantId) {
        application.setRequestType(request.requestType());
        application.setTenantId(tenantId);
        application.setCallerId(resources.caller().getId());
        application.setCallerCodeSnapshot(resources.caller().getCallerCode());
        application.setCallerNameSnapshot(resources.caller().getCallerName());
        application.setApiKeyId(resources.apiKey().getId());
        application.setApiKeyNameSnapshot(resources.apiKey().getKeyName());
        application.setApplicantUserId(userId);
        application.setApplicantNameSnapshot(username);
        application.setBusinessPurpose(request.businessPurpose().trim());
        application.setBusinessScene(request.businessScene().trim());
        application.setExpectedDailyCalls(request.expectedDailyCalls());
        application.setTicketNo(request.ticketNo() == null ? null : request.ticketNo().trim());
        application.setRequestedExpireAt(request.requestedExpireAt());
    }

    private ApplicationUpsertRequest toRequest(
            ApiPermissionApplication application,
            List<ApiPermissionApplicationItem> items) {
        return new ApplicationUpsertRequest(
                application.getRequestType(),
                application.getCallerId(),
                application.getApiKeyId(),
                items.stream().map(ApiPermissionApplicationItem::getInterfaceId).toList(),
                application.getBusinessPurpose(),
                application.getBusinessScene(),
                application.getExpectedDailyCalls(),
                application.getRequestedExpireAt(),
                application.getTicketNo(),
                items.stream().findFirst()
                        .map(ApiPermissionApplicationItem::getRequestedCacheEnabled)
                        .orElse(false),
                items.stream().findFirst()
                        .map(ApiPermissionApplicationItem::getRequestedCacheDays)
                        .orElse(null));
    }

    private ApiPermissionApplication requireOwnedApplication(Long id, Long userId) {
        ApiPermissionApplication application = requireApplication(id);
        if (!userId.equals(application.getApplicantUserId())) {
            throw forbidden("APPLICATION_ACCESS_DENIED", "只能操作本人申请");
        }
        return application;
    }

    private ApiPermissionApplication requireOwnedApplicationForUpdate(Long id, Long userId) {
        ApiPermissionApplication application = applicationMapper.selectByIdForUpdate(id);
        if (application == null) {
            throw notFound("APPLICATION_NOT_FOUND", "申请不存在");
        }
        if (!userId.equals(application.getApplicantUserId())) {
            throw forbidden("APPLICATION_ACCESS_DENIED", "只能操作本人申请");
        }
        return application;
    }

    private ApiPermissionApplication findIdempotentReplay(Long userId, String idempotencyKey) {
        return applicationMapper.selectOne(
                new LambdaQueryWrapper<ApiPermissionApplication>()
                        .eq(ApiPermissionApplication::getApplicantUserId, userId)
                        .eq(ApiPermissionApplication::getIdempotencyKey, idempotencyKey));
    }

    private ApiPermissionApplication requireSameIdempotentRequest(
            Long applicationId,
            ApiPermissionApplication replay) {
        if (!applicationId.equals(replay.getId())) {
            throw conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key 已用于其他申请");
        }
        return replay;
    }

    private void requireStatus(ApiPermissionApplication application, ApplicationStatus expected) {
        if (!expected.name().equals(application.getStatus())) {
            throw conflict("APPLICATION_STATE_CONFLICT", "申请状态已变化，请刷新后重试");
        }
    }

    private void projectCurrentTaskInternal(
            ApiPermissionApplication application,
            ApprovalEnginePort.TaskSnapshot task) {
        application.setCurrentTaskId(task != null ? task.id() : null);
        application.setCurrentTaskKey(task != null ? task.taskDefinitionKey() : null);
        application.setCurrentTaskName(task != null ? task.name() : null);
        application.setCurrentTaskCreatedAt(task != null ? task.createdAt() : null);
    }

    private String riskLevel(ApiPermissionApplication application) {
        if (application.getExpectedDailyCalls() >= 100_000) {
            return "HIGH";
        }
        if (application.getExpectedDailyCalls() >= 10_000) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String generateApplicationNo() {
        return "APA" + LocalDateTime.now().format(APPLICATION_TIME)
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private <T> T requireData(Result<T> result, String message) {
        if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
            throw new ApiPermissionException(HttpStatus.BAD_GATEWAY, "DEPENDENCY_UNAVAILABLE", message);
        }
        return result.getData();
    }

    private ApiPermissionException badRequest(String code, String message) {
        return new ApiPermissionException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiPermissionException forbidden(String code, String message) {
        return new ApiPermissionException(HttpStatus.FORBIDDEN, code, message);
    }

    private ApiPermissionException notFound(String code, String message) {
        return new ApiPermissionException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiPermissionException conflict(String code, String message) {
        return new ApiPermissionException(HttpStatus.CONFLICT, code, message);
    }

    private record ValidatedResources(
            CallerInfo caller,
            ApiKey apiKey,
            List<ApiInterfaceDTO> interfaces) {
    }
}
