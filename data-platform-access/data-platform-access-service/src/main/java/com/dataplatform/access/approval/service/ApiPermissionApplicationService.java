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
import com.dataplatform.common.util.UserContext;
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
            if (applicationMapper.insert(application) != 1) {
                throw conflict("APPLICATION_CREATE_FAILED", "申请创建失败，请重试");
            }
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
            ApiPermissionApplication application = requireOwnedApplication(id, userId, tenantId);
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
        requireTenantScope(tenantId);
        String normalizedIdempotencyKey = idempotencyKey.trim();
        ApiPermissionApplication replay = findIdempotentReplay(
                userId, tenantId, normalizedIdempotencyKey);
        if (replay != null) {
            return requireSameIdempotentRequest(id, replay);
        }

        try {
            return transactionTemplate.execute(status -> {
                ApiPermissionApplication locked = requireOwnedApplicationForUpdate(id, userId, tenantId);
                ApiPermissionApplication concurrentReplay = findIdempotentReplay(
                        userId, tenantId, normalizedIdempotencyKey);
                if (concurrentReplay != null) {
                    return requireSameIdempotentRequest(id, concurrentReplay);
                }
                requireStatus(locked, ApplicationStatus.DRAFT);
                List<ApiPermissionApplicationItem> items = listItems(id);
                if (items.isEmpty()) {
                    throw conflict("APPLICATION_ITEMS_MISSING", "申请至少需要一个接口项");
                }
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
                if (start == null || start.processInstanceId() == null
                        || start.processInstanceId().isBlank()) {
                    throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎未返回有效流程实例");
                }
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
                    userId, tenantId, normalizedIdempotencyKey);
            if (concurrentReplay != null) {
                return requireSameIdempotentRequest(id, concurrentReplay);
            }
            throw conflict("DUPLICATE_PENDING_APPLICATION", "相同 API Key 和接口已有待审批申请");
        }
    }

    @Transactional
    public ApiPermissionApplication cancel(Long id, Long userId, String username, Long tenantId) {
        ApiPermissionApplication application = requireOwnedApplication(id, userId, tenantId);
        ApplicationStatus current = parseStatus(application.getStatus());
        if (current != ApplicationStatus.DRAFT && current != ApplicationStatus.IN_REVIEW) {
            throw conflict("APPLICATION_STATE_CONFLICT", "当前状态不允许取消");
        }
        if (current == ApplicationStatus.IN_REVIEW) {
            if (application.getProcessInstanceId() == null
                    || application.getProcessInstanceId().isBlank()) {
                throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批申请缺少有效流程实例");
            }
            List<ApprovalEnginePort.TaskSnapshot> activeTasks;
            try {
                activeTasks = approvalEngine.getCurrentTasks(application.getProcessInstanceId());
            } catch (RuntimeException exception) {
                throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎查询当前任务失败");
            }
            if (activeTasks == null) {
                throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎未返回当前任务");
            }
            if (activeTasks.isEmpty()) {
                throw conflict("WITHDRAWAL_NOT_ALLOWED", "当前审批节点不允许申请人撤回");
            }
            for (ApprovalEnginePort.TaskSnapshot task : activeTasks) {
                if (task == null || task.id() == null || task.id().isBlank()) {
                    throw conflict("WITHDRAWAL_NOT_ALLOWED", "当前审批节点不允许申请人撤回");
                }
                ApprovalEnginePort.TaskPolicy policy;
                try {
                    policy = approvalEngine.getTaskPolicy(task.id());
                } catch (RuntimeException exception) {
                    throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎读取节点策略失败");
                }
                if (policy == null || !policy.allowWithdraw()) {
                    throw conflict("WITHDRAWAL_NOT_ALLOWED", "当前审批节点不允许申请人撤回");
                }
            }
            try {
                approvalEngine.terminate(application.getProcessInstanceId(), "申请人撤回");
            } catch (RuntimeException exception) {
                throw serviceUnavailable("APPROVAL_ENGINE_UNAVAILABLE", "审批引擎终止流程失败");
            }
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
        requireOwnedApplication(id, userId, tenantId);
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
            if (tenantId == null && !isPlatformAdmin()) {
                throw forbidden("TENANT_SCOPE_REQUIRED", "当前用户没有租户作用域");
            }
            if (tenantId != null) {
                query.eq(ApiPermissionApplication::getTenantId, tenantId);
            }
        } else {
            if (userId == null) {
                throw forbidden("USER_SCOPE_REQUIRED", "当前用户身份不可用");
            }
            query.eq(ApiPermissionApplication::getApplicantUserId, userId);
        }
        if (applicationStatus != null && !applicationStatus.isBlank()) {
            query.eq(ApiPermissionApplication::getStatus, applicationStatus);
        }
        query.orderByDesc(ApiPermissionApplication::getCreatedAt);
        Page<ApiPermissionApplication> result = applicationMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100)), query);
        if (result == null) {
            throw serviceUnavailable("APPLICATION_LOOKUP_UNAVAILABLE", "申请列表查询失败");
        }
        return PageResult.of(result.getRecords() == null ? List.of() : result.getRecords(),
                result.getTotal(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100));
    }

    public ApplicationDetailResponse detail(Long id) {
        ApiPermissionApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw notFound("APPLICATION_NOT_FOUND", "申请不存在");
        }
        List<ApiPermissionAction> actions = requireList(actionMapper.selectList(
                new LambdaQueryWrapper<ApiPermissionAction>()
                        .eq(ApiPermissionAction::getApplicationId, id)
                        .orderByAsc(ApiPermissionAction::getCreatedAt)),
                "申请审计记录查询失败");
        return new ApplicationDetailResponse(application, listItems(id), actions);
    }

    public List<CallerOptionResponse> eligibleCallers(
            Long userId,
            Long tenantId,
            boolean tenantWideCallerAccess) {
        if (tenantWideCallerAccess) {
            List<CallerInfo> callers = tenantId == null
                    ? callerService.list()
                    : callerService.listByTenant(tenantId);
            return callerOptions(callers);
        }
        List<Long> callerIds = requireData(identityClient.getCallerIds(userId), "身份服务返回异常");
        if (callerIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw serviceUnavailable("IDENTITY_DATA_INVALID", "身份服务返回了无效 Caller 数据");
        }
        if (callerIds.isEmpty()) {
            return List.of();
        }
        requireTenantScope(tenantId);
        return activeCallers(callerService.listByIds(callerIds)).stream()
                .filter(caller -> tenantId != null && tenantId.equals(caller.getTenantId()))
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
        List<ApiKey> keys = apiKeyService.listByCaller(callerId);
        if (keys == null) {
            throw serviceUnavailable("API_KEY_LOOKUP_UNAVAILABLE", "API Key查询失败");
        }
        return keys.stream()
                .filter(java.util.Objects::nonNull)
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
        List<ApiInterfaceDTO> options = requireInterfaces(
                interfaceClient.getOptions(keyword), "主数据服务返回异常");
        Set<Long> granted = new HashSet<>(requirePositiveIds(
                grantService.getInterfaceIdsByApiKeyId(apiKeyId), "接口授权查询失败"));
        List<ApiPermissionApplicationItem> pendingItems = requireList(itemMapper.selectList(
                new LambdaQueryWrapper<ApiPermissionApplicationItem>()
                        .eq(ApiPermissionApplicationItem::getApiKeyId, apiKeyId)
                        .in(ApiPermissionApplicationItem::getItemStatus,
                                ApplicationStatus.IN_REVIEW.name(),
                                ApplicationStatus.PROVISIONING.name())),
                "申请项查询失败");
        if (pendingItems.stream().anyMatch(item -> item == null
                || item.getInterfaceId() == null || item.getInterfaceId() <= 0)) {
            throw serviceUnavailable("APPLICATION_ITEM_DATA_INVALID", "申请项数据损坏");
        }
        Set<Long> pending = pendingItems.stream()
                .map(ApiPermissionApplicationItem::getInterfaceId)
                .collect(Collectors.toSet());
        return options.stream()
                .filter(java.util.Objects::nonNull)
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
        if (applicationId == null || applicationId <= 0) {
            throw notFound("APPLICATION_NOT_FOUND", "申请不存在");
        }
        List<ApiPermissionApplicationItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ApiPermissionApplicationItem>()
                .eq(ApiPermissionApplicationItem::getApplicationId, applicationId)
                .orderByAsc(ApiPermissionApplicationItem::getId));
        if (items == null) {
            throw serviceUnavailable("APPLICATION_ITEM_LOOKUP_UNAVAILABLE", "申请接口项查询失败");
        }
        if (items.stream().anyMatch(item -> item == null || item.getId() == null)) {
            throw serviceUnavailable("APPLICATION_ITEM_DATA_INVALID", "申请接口项数据损坏");
        }
        return items;
    }

    public ApiPermissionApplication requireApplication(Long id) {
        if (id == null || id <= 0) {
            throw notFound("APPLICATION_NOT_FOUND", "申请不存在");
        }
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
        boolean visible = isPlatformAdmin()
                || (tenantId != null
                && tenantId.equals(application.getTenantId())
                && (tenantScope
                || (userId != null && userId.equals(application.getApplicantUserId()))));
        if (!visible) {
            throw forbidden("APPLICATION_ACCESS_DENIED", "无权查看该申请");
        }
        return application;
    }

    public List<ApiInterfaceDTO> validateProvisioningResources(
            ApiPermissionApplication application) {
        if (application == null || application.getId() == null
                || application.getCallerId() == null || application.getApiKeyId() == null) {
            throw conflict("APPLICATION_DATA_INVALID", "申请资源数据不完整");
        }
        CallerInfo caller = callerService.getById(application.getCallerId());
        if (caller == null
                || application.getTenantId() == null
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
        if (interfaceIds.isEmpty() || interfaceIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw conflict("APPLICATION_DATA_INVALID", "申请接口项数据不完整");
        }
        List<ApiInterfaceDTO> interfaces = requireInterfaces(
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
        if (application == null || application.getId() == null) {
            throw badRequest("APPLICATION_INVALID", "申请不能为空且必须包含标识");
        }
        application.setUpdatedAt(LocalDateTime.now());
        if (applicationMapper.updateById(application) != 1) {
            throw conflict("APPLICATION_VERSION_CONFLICT", "申请已被并发更新，请刷新后重试");
        }
    }

    public void updateItem(ApiPermissionApplicationItem item) {
        if (item == null || item.getId() == null) {
            throw badRequest("APPLICATION_ITEM_INVALID", "申请项不能为空且必须包含标识");
        }
        item.setUpdatedAt(LocalDateTime.now());
        if (itemMapper.updateById(item) != 1) {
            throw conflict("APPLICATION_ITEM_VERSION_CONFLICT", "申请项已被并发更新，请刷新后重试");
        }
    }

    public void updateItemStatus(Long applicationId, ApplicationStatus status) {
        if (applicationId == null || status == null) {
            throw badRequest("APPLICATION_ITEM_INVALID", "申请和申请项状态不能为空");
        }
        List<ApiPermissionApplicationItem> items = listItems(applicationId);
        for (ApiPermissionApplicationItem item : items) {
            item.setItemStatus(status.name());
            updateItem(item);
        }
    }

    public void applyApprovedCachePolicy(
            Long applicationId,
            boolean cacheEnabled,
            Integer approvedCacheDays) {
        if (applicationId == null) {
            throw badRequest("APPLICATION_ITEM_INVALID", "申请标识不能为空");
        }
        if (cacheEnabled && (approvedCacheDays == null || approvedCacheDays < 1
                || approvedCacheDays > MAX_CACHE_DAYS)) {
            throw badRequest("INVALID_CACHE_POLICY", "审批缓存时效必须在 1 到 365 天之间");
        }
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
        if (application == null || application.getId() == null
                || action == null || action.isBlank()
                || actorType == null || actorType.isBlank()) {
            throw badRequest("APPLICATION_AUDIT_INVALID", "申请审计数据不完整");
        }
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
        if (actionMapper.insert(record) != 1) {
            throw new ApiPermissionException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "APPLICATION_AUDIT_WRITE_FAILED",
                    "申请审计记录写入失败，请重试");
        }
    }

    private ValidatedResources validateResources(
            ApplicationUpsertRequest request,
            Long userId,
            Long tenantId,
            boolean submitting,
        boolean tenantWideCallerAccess) {
        validateRequest(request, submitting);
        requireTenantScope(tenantId);
        if (userId == null || userId <= 0) {
            throw forbidden("USER_SCOPE_REQUIRED", "当前用户身份不可用");
        }
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
        List<ApiInterfaceDTO> interfaces = requireInterfaces(
                interfaceClient.batchGet(interfaceIds), "主数据服务返回异常");
        Map<Long, ApiInterfaceDTO> byId = interfaces.stream()
                .collect(Collectors.toMap(ApiInterfaceDTO::getId, Function.identity(), (first, second) -> first));
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
        if (tenantId == null || !tenantId.equals(caller.getTenantId())
                || !CommonStatus.ACTIVE.equals(caller.getStatus())) {
            throw forbidden("CALLER_ACCESS_DENIED", "Caller 不属于当前租户或已停用");
        }
        if (!tenantWideCallerAccess && (userId == null || userId <= 0)) {
            throw forbidden("USER_SCOPE_REQUIRED", "当前用户身份不可用");
        }
        if (tenantWideCallerAccess) {
            return caller;
        }
        CallerAccessDTO access = requireData(
                identityClient.getCallerAccess(userId, callerId), "身份服务返回异常");
        if (!access.isAllowed() || tenantId == null || !tenantId.equals(access.getTenantId())) {
            throw forbidden("CALLER_ACCESS_DENIED", "当前用户无权管理该 Caller");
        }
        return caller;
    }

    private ApiKey requireApiKey(Long apiKeyId) {
        if (apiKeyId == null || apiKeyId <= 0) {
            throw badRequest("INVALID_RESOURCE", "API Key 标识无效");
        }
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
        if (request.callerId() == null || request.callerId() <= 0
                || request.apiKeyId() == null || request.apiKeyId() <= 0) {
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
        if (application == null || interfaces == null || interfaces.isEmpty()
                || interfaces.stream().anyMatch(apiInterface -> apiInterface == null
                || apiInterface.getId() == null)) {
            throw conflict("APPLICATION_DATA_INVALID", "申请接口数据不完整");
        }
        for (ApiInterfaceDTO apiInterface : interfaces) {
            Long pending = itemMapper.selectCount(new LambdaQueryWrapper<ApiPermissionApplicationItem>()
                    .eq(ApiPermissionApplicationItem::getApiKeyId, application.getApiKeyId())
                    .eq(ApiPermissionApplicationItem::getInterfaceId, apiInterface.getId())
                    .ne(ApiPermissionApplicationItem::getApplicationId, application.getId())
                    .in(ApiPermissionApplicationItem::getItemStatus,
                            ApplicationStatus.IN_REVIEW.name(),
                            ApplicationStatus.PROVISIONING.name()));
            if (pending == null) {
                throw serviceUnavailable("APPLICATION_ITEM_LOOKUP_UNAVAILABLE", "待审批申请查询失败");
            }
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
        LambdaQueryWrapper<ApprovalProcessConfig> query = new LambdaQueryWrapper<>();
        if (tenantId == null) {
            query.eq(ApprovalProcessConfig::getTenantId, 0L);
        } else {
            query.in(ApprovalProcessConfig::getTenantId, tenantId, 0L);
        }
        query.eq(ApprovalProcessConfig::getBusinessType, businessType)
                        .eq(ApprovalProcessConfig::getEngineType, "FLOWABLE")
                        .in(ApprovalProcessConfig::getRiskLevel, riskLevel, "*")
                        .eq(ApprovalProcessConfig::getEnabled, true);
        List<ApprovalProcessConfig> configs = processConfigMapper.selectList(query);
        if (configs == null) {
            throw serviceUnavailable("APPROVAL_CONFIG_UNAVAILABLE", "审批流程配置查询失败");
        }
        return configs.stream()
                .filter(this::isUsableProcessConfig)
                .sorted(Comparator
                        .comparing((ApprovalProcessConfig config) -> tenantId != null
                                && tenantId.equals(config.getTenantId()))
                        .reversed()
                        .thenComparing(config -> riskLevel.equals(config.getRiskLevel()), Comparator.reverseOrder())
                        .thenComparing(ApprovalProcessConfig::getPriority,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElseThrow(() -> conflict(
                        "APPROVAL_PROCESS_NOT_CONFIGURED", "未配置可用的审批流程"));
    }

    private void replaceItems(
            ApiPermissionApplication application,
            List<ApiInterfaceDTO> interfaces,
            ApplicationStatus status,
            ApplicationUpsertRequest request) {
        if (application == null || application.getId() == null || interfaces == null || status == null
                || request == null) {
            throw badRequest("APPLICATION_ITEM_INVALID", "申请接口项数据不完整");
        }
        int deleted = itemMapper.delete(new LambdaQueryWrapper<ApiPermissionApplicationItem>()
                .eq(ApiPermissionApplicationItem::getApplicationId, application.getId()));
        if (deleted < 0) {
            throw serviceUnavailable("APPLICATION_ITEM_DELETE_FAILED", "申请接口项旧数据清理失败");
        }
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
            if (itemMapper.insert(item) != 1) {
                throw conflict("APPLICATION_ITEM_CREATE_FAILED", "申请接口项写入失败，请重试");
            }
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

    private ApiPermissionApplication requireOwnedApplication(Long id, Long userId, Long tenantId) {
        ApiPermissionApplication application = requireApplication(id);
        if (userId == null || !userId.equals(application.getApplicantUserId())
                || (!isPlatformAdmin()
                && (tenantId == null || !tenantId.equals(application.getTenantId())))) {
            throw forbidden("APPLICATION_ACCESS_DENIED", "只能操作本人申请");
        }
        return application;
    }

    private ApiPermissionApplication requireOwnedApplicationForUpdate(
            Long id, Long userId, Long tenantId) {
        ApiPermissionApplication application = applicationMapper.selectByIdForUpdate(id);
        if (application == null) {
            throw notFound("APPLICATION_NOT_FOUND", "申请不存在");
        }
        if (userId == null || !userId.equals(application.getApplicantUserId())
                || (!isPlatformAdmin()
                && (tenantId == null || !tenantId.equals(application.getTenantId())))) {
            throw forbidden("APPLICATION_ACCESS_DENIED", "只能操作本人申请");
        }
        return application;
    }

    private ApiPermissionApplication findIdempotentReplay(
            Long userId, Long tenantId, String idempotencyKey) {
        LambdaQueryWrapper<ApiPermissionApplication> query = new LambdaQueryWrapper<>();
        query.eq(ApiPermissionApplication::getApplicantUserId, userId)
                .eq(ApiPermissionApplication::getIdempotencyKey, idempotencyKey);
        if (tenantId == null) {
            query.isNull(ApiPermissionApplication::getTenantId);
        } else {
            query.eq(ApiPermissionApplication::getTenantId, tenantId);
        }
        return applicationMapper.selectOne(query);
    }

    private boolean isPlatformAdmin() {
        try {
            return UserContext.hasPermission("system:admin");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void requireTenantScope(Long tenantId) {
        if (tenantId == null) {
            throw forbidden("TENANT_SCOPE_REQUIRED", "当前用户没有租户作用域");
        }
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
        if (application == null || expected == null || !expected.name().equals(application.getStatus())) {
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
        if (application == null || application.getExpectedDailyCalls() == null) {
            throw conflict("APPLICATION_DATA_INVALID", "申请预计调用量数据不完整");
        }
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

    private <T> List<T> requireList(List<T> values, String message) {
        if (values == null) {
            throw serviceUnavailable("DEPENDENCY_UNAVAILABLE", message);
        }
        return values;
    }

    private List<ApiInterfaceDTO> requireInterfaces(
            Result<List<ApiInterfaceDTO>> result, String message) {
        List<ApiInterfaceDTO> interfaces = requireData(result, message);
        if (interfaces.stream().anyMatch(apiInterface -> apiInterface == null
                || apiInterface.getId() == null || apiInterface.getId() <= 0)) {
            throw serviceUnavailable("DEPENDENCY_DATA_INVALID", "主数据服务返回了无效接口数据");
        }
        return interfaces;
    }

    private List<Long> requirePositiveIds(List<Long> ids, String message) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw serviceUnavailable("DEPENDENCY_DATA_INVALID", message);
        }
        return ids;
    }

    private List<CallerOptionResponse> callerOptions(List<CallerInfo> callers) {
        return requireList(callers, "Caller查询失败").stream()
                .filter(caller -> caller != null && caller.getId() != null)
                .filter(caller -> CommonStatus.ACTIVE.equals(caller.getStatus()))
                .map(caller -> new CallerOptionResponse(
                        caller.getId(), caller.getCallerCode(), caller.getCallerName()))
                .toList();
    }

    private List<CallerInfo> activeCallers(List<CallerInfo> callers) {
        return requireList(callers, "Caller查询失败").stream()
                .filter(caller -> caller != null && caller.getId() != null)
                .filter(caller -> CommonStatus.ACTIVE.equals(caller.getStatus()))
                .toList();
    }

    private ApplicationStatus parseStatus(String status) {
        try {
            return ApplicationStatus.valueOf(status);
        } catch (RuntimeException exception) {
            throw conflict("APPLICATION_DATA_INVALID", "申请状态数据损坏");
        }
    }

    private boolean isUsableProcessConfig(ApprovalProcessConfig config) {
        return config != null
                && config.getTenantId() != null
                && config.getRiskLevel() != null
                && !config.getRiskLevel().isBlank()
                && config.getPriority() != null
                && config.getProcessDefinitionKey() != null
                && !config.getProcessDefinitionKey().isBlank()
                && config.getApproverGroup() != null
                && !config.getApproverGroup().isBlank();
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

    private ApiPermissionException serviceUnavailable(String code, String message) {
        return new ApiPermissionException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    private record ValidatedResources(
            CallerInfo caller,
            ApiKey apiKey,
            List<ApiInterfaceDTO> interfaces) {
    }
}
