package com.dataplatform.call.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.call.api.dto.CallRecordDTO;
import com.dataplatform.call.api.dto.DataQueryReqDTO;
import com.dataplatform.call.api.dto.DataQueryRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "data-platform-call-service", path = "/call")
public interface CallFeignClient {

    @PostMapping("/query")
    Result<DataQueryRespDTO> query(@RequestBody DataQueryReqDTO req);

    @GetMapping("/record/{id}")
    Result<CallRecordDTO> getCallRecord(@PathVariable("id") Long id);
}
