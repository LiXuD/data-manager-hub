package com.dataplatform.billing.controller;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.api.dto.BillingCalculateRespDTO;
import com.dataplatform.billing.api.dto.BillingRuleDTO;
import com.dataplatform.billing.api.dto.BillingTierDTO;
import com.dataplatform.billing.api.feign.BillingInternalFeignClient;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.service.BillingService;
import com.dataplatform.billing.service.BillingUsageRecorder;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;
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
    @GetMapping("/rules/by-vendor-and-interface")
    public Result<BillingRuleDTO> getRuleByVendorAndInterface(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("interfaceCode") String interfaceCode) {
        BillingRule rule = billingService.getRuleByVendorAndInterface(vendorCode, interfaceCode);
        if (rule == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(rule));
    }

    @Override
    @PostMapping("/calculate")
    @Transactional
    public Result<BillingCalculateRespDTO> calculateCost(@RequestBody BillingCalculateReqDTO dto) {
        int callCount = dto.getCallCount() != null ? dto.getCallCount() : 1;
        long latency = dto.getLatency() != null ? dto.getLatency() : 0L;
        boolean success = dto.getSuccess() == null || Boolean.TRUE.equals(dto.getSuccess());
        boolean billable = dto.getBillable() == null || Boolean.TRUE.equals(dto.getBillable());
        LocalDate billingDate = dto.getCallTime() != null
                ? dto.getCallTime().toLocalDate()
                : LocalDate.now();
        BigDecimal cost = success && billable
                ? billingService.calculateCost(dto.getVendorCode(), dto.getInterfaceCode(), callCount, latency,
                        dto.getRequestId(), billingDate)
                : BigDecimal.ZERO;
        BillingCalculateRespDTO response = new BillingCalculateRespDTO();
        response.setCost(cost);
        BillingRule rule = billingService.getRuleByVendorAndInterface(
                dto.getVendorCode(), dto.getInterfaceCode());
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
        if (entity.getTiers() != null) {
            dto.setTiers(entity.getTiers().stream().map(tier -> {
                BillingTierDTO tierDTO = new BillingTierDTO();
                BeanUtils.copyProperties(tier, tierDTO);
                return tierDTO;
            }).toList());
        }
        return dto;
    }
}
