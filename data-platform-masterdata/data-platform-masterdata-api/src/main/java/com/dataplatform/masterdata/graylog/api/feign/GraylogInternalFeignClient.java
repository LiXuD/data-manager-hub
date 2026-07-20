package com.dataplatform.masterdata.graylog.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "data-platform-masterdata", contextId = "masterdataGraylogInternalClient",
        path = "/internal/v1/masterdata/gray-rules")
@InternalFeignContract
public interface GraylogInternalFeignClient {

    @GetMapping("/active/{serviceName}")
    Result<GrayRuleDTO> getActiveRule(@PathVariable("serviceName") String serviceName);
}
