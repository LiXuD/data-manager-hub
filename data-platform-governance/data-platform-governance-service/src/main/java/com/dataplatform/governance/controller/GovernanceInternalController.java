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
}
