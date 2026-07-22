package com.dataplatform.billing.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.api.dto.BillingChargeRespDTO;
import com.dataplatform.billing.api.dto.BillingMeteringPolicyDTO;
import java.time.LocalDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "data-platform-billing", contextId = "billingInternalFeignClient",
        path = "/internal/v1/billing")
@InternalFeignContract
public interface BillingInternalFeignClient {

    @GetMapping("/metering-policy")
    Result<BillingMeteringPolicyDTO> getMeteringPolicy(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("interfaceCode") String interfaceCode,
            @RequestParam("callTime") LocalDateTime callTime);

    @PostMapping("/charge")
    Result<BillingChargeRespDTO> charge(@RequestBody BillingChargeReqDTO dto);
}
