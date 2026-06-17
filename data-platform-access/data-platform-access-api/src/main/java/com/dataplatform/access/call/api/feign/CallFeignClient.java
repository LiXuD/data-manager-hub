package com.dataplatform.access.call.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.access.call.api.dto.CallRecordDTO;
import com.dataplatform.access.call.api.dto.DataQueryReqDTO;
import com.dataplatform.access.call.api.dto.DataQueryRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 访问域数据调用的 Call Feign Client。
 * <p>OpenFeign 远程调用契约，供其他服务依赖 api 模块完成跨域调用。</p>
 */
@FeignClient(name = "data-platform-access", contextId = "accessCallFeignClient", path = "/access/call")
public interface CallFeignClient {

    /**
     * 发起一次标准数据查询调用，并返回供应商响应、计费和调用链路信息。
     */
    @PostMapping("/query")
    Result<DataQueryRespDTO> query(@RequestBody DataQueryReqDTO req);

    /**
     * 按调用记录主键查询一次历史调用明细。
     */
    @GetMapping("/record/{id}")
    Result<CallRecordDTO> getCallRecord(@PathVariable("id") Long id);
}
