package com.dataplatform.access.caller.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import com.dataplatform.access.caller.mapper.ApiKeyInterfaceMapper;
import com.dataplatform.access.approval.domain.GrantSource;
import com.dataplatform.access.approval.domain.GrantStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 访问域调用方的 Api Key Interface Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class ApiKeyInterfaceService extends ServiceImpl<ApiKeyInterfaceMapper, ApiKeyInterface> {

    public List<Long> getInterfaceIdsByApiKeyId(Long apiKeyId) {
        LambdaQueryWrapper<ApiKeyInterface> wrapper = activeGrantQuery(apiKeyId, null);
        return list(wrapper).stream()
                .map(ApiKeyInterface::getInterfaceId)
                .collect(Collectors.toList());
    }

    public boolean hasInterfacePermission(Long apiKeyId, Long interfaceId) {
        return findEffectiveGrant(apiKeyId, interfaceId) != null;
    }

    public ApiKeyInterface findEffectiveGrant(Long apiKeyId, Long interfaceId) {
        if (apiKeyId == null || interfaceId == null) {
            return null;
        }
        return getOne(activeGrantQuery(apiKeyId, interfaceId).last("LIMIT 1"), false);
    }

    @Transactional
    public void assignInterfaces(Long apiKeyId, List<Long> interfaceIds) {
        if (apiKeyId == null) {
            throw new ApiPermissionException(
                    HttpStatus.BAD_REQUEST, "API_KEY_ID_MISSING", "API Key标识不能为空");
        }
        if (interfaceIds != null && interfaceIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ApiPermissionException(
                    HttpStatus.BAD_REQUEST, "API_KEY_INTERFACE_INVALID", "接口列表包含无效数据");
        }
        // 删除旧的授权
        LambdaQueryWrapper<ApiKeyInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyInterface::getApiKeyId, apiKeyId);
        remove(wrapper);

        // 新增授权
        if (interfaceIds != null && !interfaceIds.isEmpty()) {
            List<ApiKeyInterface> records = interfaceIds.stream()
                    .map(id -> {
                        ApiKeyInterface record = new ApiKeyInterface();
                        record.setApiKeyId(apiKeyId);
                        record.setInterfaceId(id);
                        record.setGrantSource(GrantSource.LEGACY_ADMIN.name());
                        record.setStatus(GrantStatus.ACTIVE.name());
                        record.setCacheEnabled(false);
                        record.setApprovedCacheDays(null);
                        record.setEffectiveAt(LocalDateTime.now());
                        record.setUpdatedAt(LocalDateTime.now());
                        record.setVersion(0);
                        return record;
                    })
                    .collect(Collectors.toList());
            if (!saveBatch(records)) {
                throw new ApiPermissionException(
                        HttpStatus.CONFLICT,
                        "API_KEY_INTERFACE_ASSIGNMENT_FAILED",
                        "API Key接口授权写入失败，请重试");
            }
        }
    }

    @Transactional
    public ApiKeyInterface grant(
            Long apiKeyId,
            Long interfaceId,
            GrantSource source,
            Long applicationItemId,
            LocalDateTime expireAt,
            Long actorUserId,
            boolean cacheEnabled,
            Integer approvedCacheDays) {
        if (apiKeyId == null || interfaceId == null || source == null) {
            throw new ApiPermissionException(
                    HttpStatus.BAD_REQUEST,
                    "API_KEY_INTERFACE_INVALID",
                    "API Key、接口和授权来源不能为空");
        }
        ApiKeyInterface grant = getOne(new LambdaQueryWrapper<ApiKeyInterface>()
                .eq(ApiKeyInterface::getApiKeyId, apiKeyId)
                .eq(ApiKeyInterface::getInterfaceId, interfaceId));
        LocalDateTime now = LocalDateTime.now();
        if (grant == null) {
            grant = new ApiKeyInterface();
            grant.setApiKeyId(apiKeyId);
            grant.setInterfaceId(interfaceId);
            grant.setCreatedBy(actorUserId);
            grant.setCreatedAt(now);
            grant.setVersion(0);
        }
        grant.setGrantSource(source.name());
        grant.setApplicationItemId(applicationItemId);
        grant.setStatus(GrantStatus.ACTIVE.name());
        grant.setCacheEnabled(cacheEnabled);
        grant.setApprovedCacheDays(cacheEnabled ? approvedCacheDays : null);
        grant.setEffectiveAt(now);
        grant.setExpireAt(expireAt);
        grant.setRevokedAt(null);
        grant.setRevokedBy(null);
        grant.setRevokeReason(null);
        grant.setUpdatedAt(now);
        if (grant.getId() == null) {
            if (!save(grant)) {
                throw new ApiPermissionException(
                        HttpStatus.CONFLICT,
                        "API_KEY_INTERFACE_GRANT_FAILED",
                        "接口授权写入失败，请重试");
            }
        } else {
            if (!updateById(grant)) {
                throw new ApiPermissionException(
                        HttpStatus.CONFLICT,
                        "API_KEY_INTERFACE_GRANT_FAILED",
                        "接口授权更新失败，请刷新后重试");
            }
        }
        return grant;
    }

    @Transactional
    public boolean revoke(Long grantId, Long actorUserId, String reason) {
        ApiKeyInterface grant = getById(grantId);
        if (grant == null || !GrantStatus.ACTIVE.name().equals(grant.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        grant.setStatus(GrantStatus.REVOKED.name());
        grant.setRevokedAt(now);
        grant.setRevokedBy(actorUserId);
        grant.setRevokeReason(reason);
        grant.setUpdatedAt(now);
        return updateById(grant);
    }

    public List<ApiKeyInterface> listEffectiveGrants(Long apiKeyId) {
        return list(activeGrantQuery(apiKeyId, null));
    }

    private LambdaQueryWrapper<ApiKeyInterface> activeGrantQuery(Long apiKeyId, Long interfaceId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<ApiKeyInterface> wrapper = new LambdaQueryWrapper<ApiKeyInterface>()
                .eq(ApiKeyInterface::getApiKeyId, apiKeyId)
                .eq(ApiKeyInterface::getStatus, GrantStatus.ACTIVE.name())
                .le(ApiKeyInterface::getEffectiveAt, now)
                .and(condition -> condition.isNull(ApiKeyInterface::getExpireAt)
                        .or()
                        .gt(ApiKeyInterface::getExpireAt, now));
        if (interfaceId != null) {
            wrapper.eq(ApiKeyInterface::getInterfaceId, interfaceId);
        }
        return wrapper;
    }
}
