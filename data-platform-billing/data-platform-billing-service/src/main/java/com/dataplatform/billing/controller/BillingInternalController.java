package com.dataplatform.billing.controller;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.api.dto.BillingCalculateRespDTO;
import com.dataplatform.billing.api.dto.BillingRuleDTO;
import com.dataplatform.billing.api.feign.BillingInternalFeignClient;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.service.BillingService;
import com.dataplatform.billing.service.BillingUsageRecorder;
import java.math.BigDecimal;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 计费域计费计算的 Billing Internal Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/internal/v1/billing")
@InternalScope("billing:calculate")
public class BillingInternalController implements BillingInternalFeignClient {

    private final BillingService billingService;
    private final BillingUsageRecorder billingUsageRecorder;

    public BillingInternalController(BillingService billingService,
                                     BillingUsageRecorder billingUsageRecorder) {
        this.billingService = billingService;
        this.billingUsageRecorder = billingUsageRecorder;
    }

    @Override
    @GetMapping("/rules/by-vendor-and-data-type")
    public Result<BillingRuleDTO> getRuleByVendorAndDataType(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataType") String dataType) {
        BillingRule rule = billingService.getRuleByVendorAndDataType(vendorCode, dataType);
        if (rule == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(rule));
    }

    @Override
    @PostMapping("/calculate")
    public Result<BillingCalculateRespDTO> calculateCost(@RequestBody BillingCalculateReqDTO dto) {
        int callCount = dto.getCallCount() != null ? dto.getCallCount() : 1;
        long latency = dto.getLatency() != null ? dto.getLatency() : 0L;
        boolean success = dto.getSuccess() == null || Boolean.TRUE.equals(dto.getSuccess());
        boolean billable = dto.getBillable() == null || Boolean.TRUE.equals(dto.getBillable());
        BigDecimal cost = success && billable
                ? billingService.calculateCost(dto.getVendorCode(), dto.getDataType(), callCount, latency)
                : BigDecimal.ZERO;
        BillingCalculateRespDTO response = new BillingCalculateRespDTO();
        response.setCost(cost);
        BillingRule rule = billingService.getRuleByVendorAndDataType(dto.getVendorCode(), dto.getDataType());
        if (rule != null) {
            response.setBillingType(rule.getBillingType());
            response.setRuleName(rule.getRuleName());
        }
        billingUsageRecorder.record(dto, cost);
        return Result.success(response);
    }

    private BillingRuleDTO toDTO(BillingRule entity) {
        BillingRuleDTO dto = new BillingRuleDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
