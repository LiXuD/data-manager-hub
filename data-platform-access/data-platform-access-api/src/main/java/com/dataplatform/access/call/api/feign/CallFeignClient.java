package com.dataplatform.access.call.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.access.call.api.dto.CallRecordDTO;
import com.dataplatform.access.call.api.dto.DataQueryReqDTO;
import com.dataplatform.access.call.api.dto.DataQueryRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "data-platform-access", contextId = "accessCallFeignClient", path = "/access/call")
public interface CallFeignClient {

    @PostMapping("/query")
    Result<DataQueryRespDTO> query(@RequestBody DataQueryReqDTO req);

    @GetMapping("/record/{id}")
    Result<CallRecordDTO> getCallRecord(@PathVariable("id") Long id);
}
