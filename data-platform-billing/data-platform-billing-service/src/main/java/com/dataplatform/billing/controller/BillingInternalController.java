package com.dataplatform.billing.controller;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingRuleDTO;
import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.service.BillingService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 计费域计费计算的 Billing Internal Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/billing/internal")
public class BillingInternalController {

    @Autowired
    private BillingService billingService;

    @GetMapping("/rule/byVendorAndDataType")
    public Result<BillingRuleDTO> getRuleByVendorAndDataType(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataType") String dataType) {
        BillingRule rule = billingService.getRuleByVendorAndDataType(vendorCode, dataType);
        if (rule == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(rule));
    }

    private BillingRuleDTO toDTO(BillingRule entity) {
        BillingRuleDTO dto = new BillingRuleDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
