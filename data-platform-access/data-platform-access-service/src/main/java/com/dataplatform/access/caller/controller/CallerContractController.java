package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.api.dto.ApiKeyDTO;
import com.dataplatform.access.caller.api.dto.CallerInfoDTO;
import com.dataplatform.access.caller.api.feign.CallerFeignClient;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.CommonStatus;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访问域调用方的 Caller Contract Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/access/caller")
public class CallerContractController implements CallerFeignClient {

    @Autowired
    private CallerService callerService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyInterfaceService apiKeyInterfaceService;

    @Override
    public Result<CallerInfoDTO> getCaller(Long id) {
        return Result.success(toCallerDTO(callerService.getById(id)));
    }

    @Override
    public Result<ApiKeyDTO> getApiKey(String apiKey) {
        return Result.success(toApiKeyDTO(apiKeyService.getByKey(apiKey)));
    }

    @Override
    public Result<ApiKeyDTO> validateApiKey(String apiKey) {
        return Result.success(toApiKeyDTO(apiKeyService.getByKey(apiKey)));
    }

    @Override
    public Result<Boolean> hasInterfacePermission(Long apiKeyId, Long interfaceId) {
        return Result.success(apiKeyInterfaceService.hasInterfacePermission(apiKeyId, interfaceId));
    }

    @Override
    public Result<Long> createCaller(CallerInfoDTO dto) {
        CallerInfo caller = new CallerInfo();
        BeanUtils.copyProperties(dto, caller);
        caller.setId(null);
        caller.setStatus(CommonStatus.ACTIVE);
        callerService.save(caller);
        return Result.success(caller.getId());
    }

    @Override
    public Result<Void> updateCaller(Long id, CallerInfoDTO dto) {
        CallerInfo caller = new CallerInfo();
        BeanUtils.copyProperties(dto, caller);
        caller.setId(id);
        callerService.updateById(caller);
        return Result.success();
    }

    private CallerInfoDTO toCallerDTO(CallerInfo entity) {
        if (entity == null) {
            return null;
        }
        CallerInfoDTO dto = new CallerInfoDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        dto.setCreateTime(entity.getCreatedAt());
        dto.setUpdateTime(entity.getUpdatedAt());
        return dto;
    }

    private ApiKeyDTO toApiKeyDTO(ApiKey entity) {
        if (entity == null) {
            return null;
        }
        ApiKeyDTO dto = new ApiKeyDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        dto.setCreateTime(entity.getCreatedAt());
        return dto;
    }
}
