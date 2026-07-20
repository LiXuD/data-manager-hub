package com.dataplatform.masterdata.graylog.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleDTO;
import com.dataplatform.masterdata.graylog.api.feign.GraylogInternalFeignClient;
import com.dataplatform.masterdata.graylog.entity.GrayRule;
import com.dataplatform.masterdata.graylog.service.GraylogService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/masterdata/gray-rules")
@InternalScope("masterdata:read")
public class GraylogInternalController implements GraylogInternalFeignClient {

    private final GraylogService graylogService;

    public GraylogInternalController(GraylogService graylogService) {
        this.graylogService = graylogService;
    }

    @Override
    public Result<GrayRuleDTO> getActiveRule(@PathVariable("serviceName") String serviceName) {
        GrayRule rule = graylogService.getActiveRule(serviceName);
        if (rule == null) {
            return Result.error(404, "服务无活跃灰度规则");
        }
        GrayRuleDTO dto = new GrayRuleDTO();
        BeanUtils.copyProperties(rule, dto);
        if (rule.getStatus() != null) {
            dto.setStatus(rule.getStatus().getCode());
        }
        return Result.success(dto);
    }
}
