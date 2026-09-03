package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.api.feign.CallerInternalFeignClient;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访问域调用方内部只读校验接口。
 */
@RestController
@RequestMapping("/internal/v1/access/callers")
public class CallerInternalController implements CallerInternalFeignClient {

    private final CallerService callerService;

    public CallerInternalController(CallerService callerService) {
        this.callerService = callerService;
    }

    @Override
    @InternalScope("access:caller:read")
    public Result<List<Long>> validate(Long tenantId, List<Long> callerIds) {
        if (tenantId == null || tenantId <= 0 || callerIds == null
                || callerIds.stream().anyMatch(id -> id == null || id <= 0)) {
            return Result.error(400, "租户和调用方ID不能为空且必须为正数");
        }
        Set<Long> requested = callerIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        List<Long> usable = callerService.listByTenant(tenantId)
                .stream()
                .map(CallerInfo::getId)
                .filter(requested::contains)
                .distinct()
                .toList();
        return Result.success(usable);
    }
}
