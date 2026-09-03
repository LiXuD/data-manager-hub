package com.dataplatform.governance.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.governance.api.dto.AlertRecordCreateDTO;
import com.dataplatform.governance.api.feign.GovernanceInternalFeignClient;
import com.dataplatform.governance.monitor.entity.AlertRecord;
import com.dataplatform.governance.monitor.service.AlertService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/governance")
@InternalScope("governance:alert")
public class GovernanceInternalController implements GovernanceInternalFeignClient {

    private final AlertService alertService;

    public GovernanceInternalController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public Result<Void> createAlertRecord(@RequestBody AlertRecordCreateDTO dto) {
        if (dto == null) {
            return Result.error(400, "请求体不能为空");
        }
        if (isBlank(dto.getAlertType()) || dto.getAlertType().length() > 50) {
            return Result.error(400, "告警类型不能为空且长度不能超过50");
        }
        if (isBlank(dto.getAlertTitle()) || dto.getAlertTitle().length() > 200) {
            return Result.error(400, "告警标题不能为空且长度不能超过200");
        }
        if (isBlank(dto.getLevel()) || dto.getLevel().length() > 20) {
            return Result.error(400, "告警级别不能为空且长度不能超过20");
        }
        if (dto.getRuleId() != null && dto.getRuleId() <= 0
                || dto.getTenantId() != null && dto.getTenantId() <= 0) {
            return Result.error(400, "告警规则或租户ID无效");
        }
        AlertRecord record = new AlertRecord();
        record.setRuleId(dto.getRuleId());
        record.setTenantId(dto.getTenantId());
        record.setAlertType(dto.getAlertType());
        record.setAlertTitle(dto.getAlertTitle());
        record.setLevel(dto.getLevel());
        record.setAlertMessage(dto.getAlertMessage());
        record.setTriggeredValue(dto.getTriggeredValue());
        record.setStatus(dto.getStatus());
        alertService.saveRecord(record);
        return Result.success(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
