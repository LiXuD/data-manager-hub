package com.dataplatform.access.call.api.feign;

import com.dataplatform.access.call.api.dto.CallStatsDTO;
import com.dataplatform.access.call.api.dto.DailyCallStatsDTO;
import com.dataplatform.access.call.api.dto.VendorCallSummaryDTO;
import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "data-platform-access", contextId = "accessCallStatsInternalClient",
        path = "/internal/v1/access/call-stats")
@InternalFeignContract
public interface CallStatsInternalFeignClient {

    @GetMapping("/interfaces/{apiCode}/summary")
    Result<CallStatsDTO> getInterfaceStats(
            @PathVariable("apiCode") String apiCode,
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime,
            @RequestParam("slaThreshold") Integer slaThreshold);

    @GetMapping("/interfaces/{apiCode}/daily")
    Result<List<DailyCallStatsDTO>> getDailyInterfaceStats(
            @PathVariable("apiCode") String apiCode,
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime);

    @GetMapping("/vendors/{vendorId}/daily-summary")
    Result<VendorCallSummaryDTO> getVendorDailySummary(
            @PathVariable("vendorId") Long vendorId,
            @RequestParam("billingDate") String billingDate);
}
