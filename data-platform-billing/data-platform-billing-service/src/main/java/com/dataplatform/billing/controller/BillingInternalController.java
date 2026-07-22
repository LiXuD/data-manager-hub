package com.dataplatform.billing.controller;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.api.dto.BillingChargeRespDTO;
import com.dataplatform.billing.api.dto.BillingMeteringPolicyDTO;
import com.dataplatform.billing.api.feign.BillingInternalFeignClient;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.billing.service.BillingChargeService;
import com.dataplatform.billing.service.BillingPlanService;
import org.springframework.web.bind.annotation.*;

/**
 * 计费域计费计算的 Billing Internal Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/internal/v1/billing")
@InternalScope("billing:calculate")
public class BillingInternalController implements BillingInternalFeignClient {

    private final BillingPlanService billingPlanService;
    private final BillingChargeService billingChargeService;

    public BillingInternalController(BillingPlanService billingPlanService,
                                     BillingChargeService billingChargeService) {
        this.billingPlanService = billingPlanService;
        this.billingChargeService = billingChargeService;
    }

    @Override
    @GetMapping("/metering-policy")
    public Result<BillingMeteringPolicyDTO> getMeteringPolicy(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("interfaceCode") String interfaceCode,
            @RequestParam("callTime") java.time.LocalDateTime callTime) {
        return Result.success(billingPlanService.resolvePolicy(vendorCode, interfaceCode, callTime));
    }

    @Override
    @PostMapping("/charge")
    public Result<BillingChargeRespDTO> charge(@RequestBody BillingChargeReqDTO dto) {
        return Result.success(billingChargeService.charge(dto));
    }
}
