package com.dataplatform.billing.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingDailyDTO;
import com.dataplatform.billing.api.dto.BillingRuleDTO;
import com.dataplatform.billing.api.dto.TenantBudgetDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "data-platform-billing-service", path = "/billing")
public interface BillingFeignClient {

    @GetMapping("/daily/{id}")
    Result<BillingDailyDTO> getBillingDaily(@PathVariable("id") Long id);

    @GetMapping("/rule/{tenantId}")
    Result<BillingRuleDTO> getBillingRule(@PathVariable("tenantId") Long tenantId);

    @GetMapping("/rule/byVendorAndDataType")
    Result<BillingRuleDTO> getRuleByVendorAndDataType(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataType") String dataType);

    @GetMapping("/budget/{tenantId}")
    Result<TenantBudgetDTO> getTenantBudget(@PathVariable("tenantId") Long tenantId);

    @PutMapping("/budget/{tenantId}")
    Result<Void> updateTenantBudget(@PathVariable("tenantId") Long tenantId, @RequestBody TenantBudgetDTO dto);
}
