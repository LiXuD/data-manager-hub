package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.api.dto.CallStatsDTO;
import com.dataplatform.access.call.api.dto.DailyCallStatsDTO;
import com.dataplatform.access.call.api.dto.VendorCallSummaryDTO;
import com.dataplatform.access.call.api.feign.CallStatsInternalFeignClient;
import com.dataplatform.access.call.service.CallStatsQueryService;
import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/access/call-stats")
@InternalScope("access:stats:read")
public class CallStatsInternalController implements CallStatsInternalFeignClient {

    private final CallStatsQueryService service;

    public CallStatsInternalController(CallStatsQueryService service) {
        this.service = service;
    }

    @Override
    public Result<CallStatsDTO> getInterfaceStats(String apiCode, String startTime,
                                                  String endTime, Integer slaThreshold) {
        return Result.success(service.getInterfaceStats(apiCode, LocalDateTime.parse(startTime),
                LocalDateTime.parse(endTime), slaThreshold));
    }

    @Override
    public Result<List<DailyCallStatsDTO>> getDailyInterfaceStats(String apiCode, String startTime,
                                                                  String endTime) {
        return Result.success(service.getDailyInterfaceStats(apiCode, LocalDateTime.parse(startTime),
                LocalDateTime.parse(endTime)));
    }

    @Override
    public Result<VendorCallSummaryDTO> getVendorDailySummary(Long vendorId, String billingDate) {
        return Result.success(service.getVendorDailySummary(vendorId, LocalDate.parse(billingDate)));
    }
}
