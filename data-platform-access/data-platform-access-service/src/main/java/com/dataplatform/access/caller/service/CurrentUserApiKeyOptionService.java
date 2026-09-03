package com.dataplatform.access.caller.service;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.vo.CurrentUserApiKeyOptionVO;
import com.dataplatform.access.caller.vo.CurrentUserApiKeyOptionsVO;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.identity.api.feign.IdentityAccessInternalFeignClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 查询当前登录用户关联系统下可用于测试调用的 API Key。
 */
@Service
public class CurrentUserApiKeyOptionService {

    private final IdentityAccessInternalFeignClient identityAccessClient;
    private final CallerService callerService;
    private final ApiKeyService apiKeyService;

    public CurrentUserApiKeyOptionService(
            IdentityAccessInternalFeignClient identityAccessClient,
            CallerService callerService,
            ApiKeyService apiKeyService) {
        this.identityAccessClient = identityAccessClient;
        this.callerService = callerService;
        this.apiKeyService = apiKeyService;
    }

    public CurrentUserApiKeyOptionsVO listOptions(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return new CurrentUserApiKeyOptionsVO(false, List.of());
        }
        Result<List<Long>> callerIdsResult = identityAccessClient.getCallerIds(userId);
        if (callerIdsResult == null
                || !Integer.valueOf(200).equals(callerIdsResult.getCode())
                || callerIdsResult.getData() == null) {
            throw new IllegalStateException("身份服务返回用户关联系统数据异常");
        }

        List<Long> callerIds = callerIdsResult.getData();
        if (callerIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("身份服务返回空的用户关联系统ID");
        }
        if (callerIds.isEmpty()) {
            return new CurrentUserApiKeyOptionsVO(false, List.of());
        }

        List<CallerInfo> callerRecords = callerService.listByIds(callerIds);
        if (callerRecords == null) {
            throw new IllegalStateException("访问服务返回调用方数据异常");
        }
        List<CallerInfo> callers = callerRecords.stream()
                .filter(Objects::nonNull)
                .filter(caller -> !Boolean.TRUE.equals(caller.getDeleted()))
                .filter(caller -> tenantId.equals(caller.getTenantId()))
                .filter(caller -> CommonStatus.ACTIVE.equals(caller.getStatus()))
                .sorted(Comparator.comparing(
                        CallerInfo::getCallerName,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
        if (callers.isEmpty()) {
            return new CurrentUserApiKeyOptionsVO(true, List.of());
        }

        LocalDateTime now = LocalDateTime.now();
        List<CurrentUserApiKeyOptionVO> options = new ArrayList<>();
        for (CallerInfo caller : callers) {
            List<ApiKey> keys = apiKeyService.listByCaller(caller.getId());
            if (keys == null) {
                throw new IllegalStateException("访问服务返回API Key数据异常");
            }
            keys.stream()
                    .filter(Objects::nonNull)
                    .filter(key -> ApiKeyStatus.ACTIVE.equals(key.getStatus()))
                    .filter(key -> !Boolean.TRUE.equals(key.getDeleted()))
                    .filter(key -> key.getCallerId() != null && caller.getId().equals(key.getCallerId()))
                    .filter(key -> key.getApiKey() != null && !key.getApiKey().isBlank())
                    .filter(key -> key.getExpireTime() == null || key.getExpireTime().isAfter(now))
                    .sorted(Comparator.comparing(
                            ApiKey::getKeyName,
                            Comparator.nullsLast(String::compareTo)))
                    .map(key -> toOption(caller, key))
                    .forEach(options::add);
        }
        return new CurrentUserApiKeyOptionsVO(true, List.copyOf(options));
    }

    public ApiKey findUsableKey(Long userId, Long tenantId, Long apiKeyId) {
        if (apiKeyId == null) {
            return null;
        }
        boolean selectable = listOptions(userId, tenantId).getOptions().stream()
                .anyMatch(option -> apiKeyId.equals(option.getId()));
        if (!selectable) {
            return null;
        }
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        LocalDateTime now = LocalDateTime.now();
        if (apiKey == null || !ApiKeyStatus.ACTIVE.equals(apiKey.getStatus())
                || Boolean.TRUE.equals(apiKey.getDeleted())
                || apiKey.getApiKey() == null || apiKey.getApiKey().isBlank()
                || (apiKey.getExpireTime() != null && !apiKey.getExpireTime().isAfter(now))) {
            return null;
        }
        if (apiKey.getCallerId() == null) {
            return null;
        }
        CallerInfo caller = callerService.getById(apiKey.getCallerId());
        if (caller == null || Boolean.TRUE.equals(caller.getDeleted())
                || !tenantId.equals(caller.getTenantId())
                || !CommonStatus.ACTIVE.equals(caller.getStatus())) {
            return null;
        }
        return apiKey;
    }

    private CurrentUserApiKeyOptionVO toOption(CallerInfo caller, ApiKey key) {
        return new CurrentUserApiKeyOptionVO(
                key.getId(),
                caller.getId(),
                caller.getCallerCode(),
                caller.getCallerName(),
                key.getKeyName(),
                mask(key.getApiKey()));
    }

    private String mask(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
