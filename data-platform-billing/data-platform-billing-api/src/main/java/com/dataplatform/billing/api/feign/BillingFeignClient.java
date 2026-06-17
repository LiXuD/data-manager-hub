package com.dataplatform.billing.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.api.dto.BillingCalculateRespDTO;
import com.dataplatform.billing.api.dto.BillingDailyDTO;
import com.dataplatform.billing.api.dto.BillingRuleDTO;
import com.dataplatform.billing.api.dto.TenantBudgetDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 计费域计费计算的 Billing Feign Client。
 * <p>OpenFeign 远程调用契约，供其他服务依赖 api 模块完成跨域调用。</p>
 */
@FeignClient(name = "data-platform-billing", contextId = "billingFeignClient", path = "/billing")
public interface BillingFeignClient {

    /**
     * 查询指定日账单明细。
     */
    @GetMapping("/daily/{id}")
    Result<BillingDailyDTO> getBillingDaily(@PathVariable("id") Long id);

    /**
     * 查询租户维度的默认计费规则。
     */
    @GetMapping("/rule/{tenantId}")
    Result<BillingRuleDTO> getBillingRule(@PathVariable("tenantId") Long tenantId);

    /**
     * 查询厂商和数据类型组合对应的计费规则。
     */
    @GetMapping("/rule/byVendorAndDataType")
    Result<BillingRuleDTO> getRuleByVendorAndDataType(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataType") String dataType);

    /**
     * 查询租户预算和阈值配置。
     */
    @GetMapping("/budget/{tenantId}")
    Result<TenantBudgetDTO> getTenantBudget(@PathVariable("tenantId") Long tenantId);

    /**
     * 更新租户预算和告警阈值配置。
     */
    @PutMapping("/budget/{tenantId}")
    Result<Void> updateTenantBudget(@PathVariable("tenantId") Long tenantId, @RequestBody TenantBudgetDTO dto);

    /**
     * 根据调用上下文计算本次数据调用费用。
     */
    @PostMapping("/calculate")
    Result<BillingCalculateRespDTO> calculateCost(@RequestBody BillingCalculateReqDTO dto);
}
