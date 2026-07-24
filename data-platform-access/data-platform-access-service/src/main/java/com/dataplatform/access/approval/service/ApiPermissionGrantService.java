package com.dataplatform.access.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.ApiKeyOptionResponse;
import com.dataplatform.access.approval.api.CallerOptionResponse;
import com.dataplatform.access.approval.api.EmergencyGrantRequest;
import com.dataplatform.access.approval.api.GrantResponse;
import com.dataplatform.access.approval.api.InterfaceOptionResponse;
import com.dataplatform.access.approval.domain.ApiPermissionAction;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApiPermissionApplicationItem;
import com.dataplatform.access.approval.domain.GrantSource;
import com.dataplatform.access.approval.domain.GrantStatus;
import com.dataplatform.access.approval.mapper.ApiPermissionActionMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationItemMapper;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApiPermissionGrantService {

    private final ApiKeyInterfaceService grantService;
    private final CallerService callerService;
    private final ApiKeyService apiKeyService;
    private final ApiInterfaceFeignClient interfaceClient;
    private final ApiPermissionApplicationService applicationService;
    private final ApiPermissionApplicationItemMapper itemMapper;
    private final ApiPermissionActionMapper actionMapper;
    private final TransactionTemplate transactionTemplate;

    public ApiPermissionGrantService(
            ApiKeyInterfaceService grantService,
            CallerService callerService,
            ApiKeyService apiKeyService,
            ApiInterfaceFeignClient interfaceClient,
            ApiPermissionApplicationService applicationService,
            ApiPermissionApplicationItemMapper itemMapper,
            ApiPermissionActionMapper actionMapper,
            PlatformTransactionManager transactionManager) {
        this.grantService = grantService;
        this.callerService = callerService;
        this.apiKeyService = apiKeyService;
        this.interfaceClient = interfaceClient;
        this.applicationService = applicationService;
        this.itemMapper = itemMapper;
        this.actionMapper = actionMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<GrantResponse> list(Long tenantId, String status) {
        List<ApiKeyInterface> grants = grantService.list(
                new LambdaQueryWrapper<ApiKeyInterface>()
                        .orderByDesc(ApiKeyInterface::getUpdatedAt)
                        .last("LIMIT 1000"));
        Set<Long> interfaceIds = grants.stream()
                .map(ApiKeyInterface::getInterfaceId)
                .collect(Collectors.toSet());
        Map<Long, ApiInterfaceDTO> interfaces = interfaceIds.isEmpty()
                ? Collections.emptyMap()
                : requireData(interfaceClient.batchGet(List.copyOf(interfaceIds))).stream()
                        .collect(Collectors.toMap(ApiInterfaceDTO::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        return grants.stream()
                .map(grant -> toResponse(grant, interfaces.get(grant.getInterfaceId()), now))
                .filter(response -> response != null && tenantId.equals(response.tenantId()))
                .filter(response -> status == null
                        || status.isBlank()
                        || status.equalsIgnoreCase(response.status()))
                .toList();
    }

    public ApiKeyInterface revoke(
            Long grantId,
            String reason,
            Long actorUserId,
            String actorName,
            Long tenantId) {
        if (reason == null || reason.trim().length() < 5 || reason.trim().length() > 1000) {
            throw badRequest("INVALID_REVOKE_REASON", "撤销原因长度必须在 5 到 1000 字之间");
        }
        ApiKeyInterface grant = requireGrantInTenant(grantId, tenantId);
        if (!GrantStatus.ACTIVE.name().equals(grant.getStatus())
                || (grant.getExpireAt() != null
                && !grant.getExpireAt().isAfter(LocalDateTime.now()))) {
            throw conflict("GRANT_NOT_ACTIVE", "授权不存在、已撤销或已到期");
        }
        return transactionTemplate.execute(status -> {
            if (!grantService.revoke(grantId, actorUserId, reason.trim())) {
                throw conflict("GRANT_STATE_CONFLICT", "授权状态已变化，请刷新后重试");
            }
            appendGrantAction(
                    grant,
                    "REVOKE",
                    actorUserId,
                    actorName,
                    reason.trim());
            return grantService.getById(grantId);
        });
    }

    public List<ApiKeyInterface> emergencyGrant(
            EmergencyGrantRequest request,
            Long actorUserId,
            String actorName,
            Long tenantId) {
        ValidatedEmergency resources = validateEmergency(request, tenantId);
        return transactionTemplate.execute(status -> resources.interfaces().stream()
                .map(apiInterface -> {
                    ApiKeyInterface grant = grantService.grant(
                            resources.apiKey().getId(),
                            apiInterface.getId(),
                            GrantSource.EMERGENCY_ADMIN,
                            null,
                            request.expireAt(),
                            actorUserId);
                    appendDetachedAction(
                            "EMERGENCY_GRANT",
                            actorUserId,
                            actorName,
                            "紧急授权 grantId=" + grant.getId()
                                    + "，工单号：" + request.ticketNo().trim()
                                    + "，原因：" + request.reason().trim());
                    return grant;
                })
                .toList());
    }

    public List<CallerOptionResponse> emergencyCallers(Long tenantId) {
        return callerService.listByTenant(tenantId).stream()
                .filter(caller -> CommonStatus.ACTIVE.equals(caller.getStatus()))
                .map(caller -> new CallerOptionResponse(
                        caller.getId(), caller.getCallerCode(), caller.getCallerName()))
                .toList();
    }

    public List<ApiKeyOptionResponse> emergencyApiKeys(Long callerId, Long tenantId) {
        CallerInfo caller = requireEmergencyCaller(callerId, tenantId);
        LocalDateTime now = LocalDateTime.now();
        return apiKeyService.listByCaller(caller.getId()).stream()
                .filter(key -> ApiKeyStatus.ACTIVE.equals(key.getStatus()))
                .filter(key -> key.getExpireTime() == null || key.getExpireTime().isAfter(now))
                .map(key -> new ApiKeyOptionResponse(
                        key.getId(), key.getCallerId(), key.getKeyName(),
                        key.getStatus().getCode(), key.getExpireTime()))
                .toList();
    }

    public List<InterfaceOptionResponse> emergencyInterfaces(
            Long apiKeyId,
            String keyword,
            Long tenantId) {
        ApiKey apiKey = requireEmergencyApiKey(apiKeyId, tenantId);
        List<ApiInterfaceDTO> options = requireData(interfaceClient.getOptions(keyword));
        Set<Long> granted = Set.copyOf(grantService.getInterfaceIdsByApiKeyId(apiKey.getId()));
        return options.stream()
                .filter(option -> "active".equalsIgnoreCase(option.getStatus()))
                .map(option -> new InterfaceOptionResponse(
                        option.getId(),
                        option.getInterfaceCode(),
                        option.getInterfaceName(),
                        option.getStatus(),
                        granted.contains(option.getId()),
                        false))
                .toList();
    }

    private ValidatedEmergency validateEmergency(
            EmergencyGrantRequest request,
            Long tenantId) {
        if (request == null
                || request.callerId() == null
                || request.apiKeyId() == null
                || request.interfaceIds() == null
                || request.interfaceIds().isEmpty()
                || request.interfaceIds().size() > 100) {
            throw badRequest("INVALID_EMERGENCY_REQUEST", "Caller、API Key 和 1 到 100 个接口不能为空");
        }
        if (request.reason() == null
                || request.reason().trim().length() < 10
                || request.reason().trim().length() > 1000) {
            throw badRequest("INVALID_EMERGENCY_REASON", "紧急授权原因长度必须在 10 到 1000 字之间");
        }
        if (request.ticketNo() == null
                || request.ticketNo().trim().length() < 3
                || request.ticketNo().trim().length() > 100) {
            throw badRequest("INVALID_EMERGENCY_TICKET", "紧急授权工单号长度必须在 3 到 100 字之间");
        }
        LocalDateTime now = LocalDateTime.now();
        if (request.expireAt() == null
                || !request.expireAt().isAfter(now)
                || request.expireAt().isAfter(now.plusHours(24))) {
            throw badRequest("INVALID_EMERGENCY_EXPIRY", "紧急授权有效期必须在未来 24 小时内");
        }
        CallerInfo caller = callerService.getById(request.callerId());
        if (caller == null
                || !tenantId.equals(caller.getTenantId())
                || !CommonStatus.ACTIVE.equals(caller.getStatus())) {
            throw forbidden("CALLER_TENANT_DENIED", "Caller 不属于当前租户或已停用");
        }
        ApiKey apiKey = apiKeyService.getById(request.apiKeyId());
        if (apiKey == null
                || !caller.getId().equals(apiKey.getCallerId())
                || !ApiKeyStatus.ACTIVE.equals(apiKey.getStatus())
                || (apiKey.getExpireTime() != null && !apiKey.getExpireTime().isAfter(now))) {
            throw conflict("API_KEY_NOT_ACTIVE", "API Key 已停用、过期或不属于所选 Caller");
        }
        List<Long> ids = request.interfaceIds().stream().distinct().toList();
        List<ApiInterfaceDTO> interfaces = requireData(interfaceClient.batchGet(ids));
        Set<Long> returned = interfaces.stream().map(ApiInterfaceDTO::getId).collect(Collectors.toSet());
        if (!returned.containsAll(ids)
                || interfaces.stream().anyMatch(item -> !"active".equalsIgnoreCase(item.getStatus()))) {
            throw conflict("INTERFACE_NOT_ACTIVE", "部分接口不存在或已停用");
        }
        return new ValidatedEmergency(apiKey, interfaces);
    }

    private CallerInfo requireEmergencyCaller(Long callerId, Long tenantId) {
        CallerInfo caller = callerId == null ? null : callerService.getById(callerId);
        if (caller == null
                || !tenantId.equals(caller.getTenantId())
                || !CommonStatus.ACTIVE.equals(caller.getStatus())) {
            throw forbidden("CALLER_TENANT_DENIED", "Caller 不属于当前租户或已停用");
        }
        return caller;
    }

    private ApiKey requireEmergencyApiKey(Long apiKeyId, Long tenantId) {
        ApiKey apiKey = apiKeyId == null ? null : apiKeyService.getById(apiKeyId);
        CallerInfo caller = apiKey == null
                ? null
                : requireEmergencyCaller(apiKey.getCallerId(), tenantId);
        LocalDateTime now = LocalDateTime.now();
        if (caller == null
                || !ApiKeyStatus.ACTIVE.equals(apiKey.getStatus())
                || (apiKey.getExpireTime() != null && !apiKey.getExpireTime().isAfter(now))) {
            throw conflict("API_KEY_NOT_ACTIVE", "API Key 已停用、过期或不属于所选 Caller");
        }
        return apiKey;
    }

    private ApiKeyInterface requireGrantInTenant(Long grantId, Long tenantId) {
        ApiKeyInterface grant = grantService.getById(grantId);
        if (grant == null) {
            throw notFound("GRANT_NOT_FOUND", "授权不存在");
        }
        ApiKey apiKey = apiKeyService.getById(grant.getApiKeyId());
        CallerInfo caller = apiKey == null ? null : callerService.getById(apiKey.getCallerId());
        if (caller == null || !tenantId.equals(caller.getTenantId())) {
            throw forbidden("GRANT_TENANT_DENIED", "无权操作其他租户授权");
        }
        return grant;
    }

    private GrantResponse toResponse(
            ApiKeyInterface grant,
            ApiInterfaceDTO apiInterface,
            LocalDateTime now) {
        ApiKey apiKey = apiKeyService.getById(grant.getApiKeyId());
        CallerInfo caller = apiKey == null ? null : callerService.getById(apiKey.getCallerId());
        if (caller == null) {
            return null;
        }
        String projectedStatus = GrantStatus.ACTIVE.name().equals(grant.getStatus())
                && grant.getExpireAt() != null
                && !grant.getExpireAt().isAfter(now)
                ? GrantStatus.EXPIRED.name()
                : grant.getStatus();
        return new GrantResponse(
                grant.getId(),
                caller.getTenantId(),
                caller.getId(),
                caller.getCallerName(),
                apiKey.getId(),
                apiKey.getKeyName(),
                grant.getInterfaceId(),
                apiInterface != null ? apiInterface.getInterfaceCode() : null,
                apiInterface != null ? apiInterface.getInterfaceName() : null,
                grant.getGrantSource(),
                projectedStatus,
                grant.getEffectiveAt(),
                grant.getExpireAt(),
                grant.getRevokedAt(),
                grant.getRevokedBy(),
                grant.getRevokeReason());
    }

    private void appendGrantAction(
            ApiKeyInterface grant,
            String action,
            Long actorUserId,
            String actorName,
            String comment) {
        if (grant.getApplicationItemId() != null) {
            ApiPermissionApplicationItem item = itemMapper.selectById(grant.getApplicationItemId());
            if (item != null) {
                ApiPermissionApplication application =
                        applicationService.requireApplication(item.getApplicationId());
                applicationService.appendAction(
                        application,
                        action,
                        "USER",
                        actorUserId,
                        actorName,
                        application.getStatus(),
                        application.getStatus(),
                        comment,
                        null);
                return;
            }
        }
        appendDetachedAction(action, actorUserId, actorName, comment);
    }

    private void appendDetachedAction(
            String action,
            Long actorUserId,
            String actorName,
            String comment) {
        ApiPermissionAction record = new ApiPermissionAction();
        record.setAction(action);
        record.setActorType("USER");
        record.setActorUserId(actorUserId);
        record.setActorNameSnapshot(actorName);
        record.setComment(comment);
        record.setEngineType(null);
        record.setTraceId(MDC.get("traceId"));
        record.setCreatedAt(LocalDateTime.now());
        actionMapper.insert(record);
    }

    private <T> T requireData(Result<T> result) {
        if (result == null || result.getCode() == null
                || result.getCode() != 200 || result.getData() == null) {
            throw new ApiPermissionException(
                    HttpStatus.BAD_GATEWAY,
                    "DEPENDENCY_UNAVAILABLE",
                    "主数据服务返回异常");
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

    private record ValidatedEmergency(
            ApiKey apiKey,
            List<ApiInterfaceDTO> interfaces) {
    }
}
