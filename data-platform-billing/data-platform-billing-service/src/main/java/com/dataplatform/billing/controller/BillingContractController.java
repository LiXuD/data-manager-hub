package com.dataplatform.billing.controller;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.api.dto.BillingCalculateRespDTO;
import com.dataplatform.billing.api.dto.BillingDailyDTO;
import com.dataplatform.billing.api.dto.BillingRuleDTO;
import com.dataplatform.billing.api.dto.TenantBudgetDTO;
import com.dataplatform.billing.api.feign.BillingFeignClient;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.entity.TenantBudget;
import com.dataplatform.billing.service.BillingService;
import com.dataplatform.billing.service.BudgetAlertService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 计费域计费计算的 Billing Contract Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/billing")
public class BillingContractController implements BillingFeignClient {

    @Autowired
    private BillingService billingService;

    @Autowired
    private BudgetAlertService budgetAlertService;

    @Override
    public Result<BillingDailyDTO> getBillingDaily(Long id) {
        return Result.success(toBillingDailyDTO(billingService.getById(id)));
    }

    @Override
    public Result<BillingRuleDTO> getBillingRule(Long tenantId) {
        return Result.success(toBillingRuleDTO(billingService.getRuleById(tenantId)));
    }

    @Override
    public Result<BillingRuleDTO> getRuleByVendorAndDataType(String vendorCode, String dataType) {
        return Result.success(toBillingRuleDTO(billingService.getRuleByVendorAndDataType(vendorCode, dataType)));
    }

    @Override
    public Result<TenantBudgetDTO> getTenantBudget(Long tenantId) {
        return Result.success(toTenantBudgetDTO(budgetAlertService.getByTenantId(tenantId)));
    }

    @Override
    public Result<Void> updateTenantBudget(Long tenantId, TenantBudgetDTO dto) {
        TenantBudget budget = new TenantBudget();
        budget.setTenantId(tenantId);
        budget.setMonthlyBudget(dto.getMonthlyBudget());
        budget.setUsedAmount(dto.getUsedAmount());
        budgetAlertService.saveBudget(budget);
        return Result.success();
    }

    @Override
    public Result<BillingCalculateRespDTO> calculateCost(BillingCalculateReqDTO dto) {
        int callCount = dto.getCallCount() != null ? dto.getCallCount() : 1;
        long latency = dto.getLatency() != null ? dto.getLatency() : 0L;
        BigDecimal cost = billingService.calculateCost(dto.getDataType(), callCount, latency);
        BillingRule rule = billingService.getRuleByVendorAndDataType(dto.getVendorCode(), dto.getDataType());

        BillingCalculateRespDTO resp = new BillingCalculateRespDTO();
        resp.setCost(cost);
        if (rule != null) {
            resp.setBillingType(rule.getBillingType());
            resp.setRuleName(rule.getRuleName());
        }
        return Result.success(resp);
    }

    private BillingDailyDTO toBillingDailyDTO(BillingDaily entity) {
        if (entity == null) {
            return null;
        }
        BillingDailyDTO dto = new BillingDailyDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setCallerId(entity.getCallerId());
        dto.setVendorId(entity.getVendorId());
        dto.setBillingDate(entity.getBillingDate());
        dto.setTotalCalls(toInteger(entity.getCallCount()));
        dto.setSuccessCalls(toInteger(entity.getSuccessCount()));
        dto.setFailedCalls(toInteger(entity.getFailCount()));
        dto.setTotalCost(entity.getTotalCost());
        return dto;
    }

    private BillingRuleDTO toBillingRuleDTO(BillingRule entity) {
        if (entity == null) {
            return null;
        }
        BillingRuleDTO dto = new BillingRuleDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private TenantBudgetDTO toTenantBudgetDTO(TenantBudget entity) {
        if (entity == null) {
            return null;
        }
        TenantBudgetDTO dto = new TenantBudgetDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setMonthlyBudget(entity.getMonthlyBudget());
        dto.setUsedAmount(entity.getUsedAmount());
        if (entity.getMonthlyBudget() != null && entity.getUsedAmount() != null) {
            dto.setRemainingAmount(entity.getMonthlyBudget().subtract(entity.getUsedAmount()));
        }
        dto.setAlertThreshold(entity.getWarningThreshold() != null ? entity.getWarningThreshold().toPlainString() : null);
        return dto;
    }

    private Integer toInteger(Long value) {
        return value != null ? value.intValue() : null;
    }
}
