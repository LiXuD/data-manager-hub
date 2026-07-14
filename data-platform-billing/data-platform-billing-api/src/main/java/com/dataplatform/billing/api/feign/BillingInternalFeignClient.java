package com.dataplatform.billing.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.api.dto.BillingCalculateRespDTO;
import com.dataplatform.billing.api.dto.BillingRuleDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "data-platform-billing", contextId = "billingInternalFeignClient",
        path = "/internal/v1/billing")
@InternalFeignContract
public interface BillingInternalFeignClient {

    @GetMapping("/rules/by-vendor-and-data-type")
    Result<BillingRuleDTO> getRuleByVendorAndDataType(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataType") String dataType);

    @PostMapping("/calculate")
    Result<BillingCalculateRespDTO> calculateCost(@RequestBody BillingCalculateReqDTO dto);
}
