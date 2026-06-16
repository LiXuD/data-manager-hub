package com.dataplatform.masterdata.interface_.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "data-platform-masterdata", contextId = "masterdataApiInterfaceClient")
public interface ApiInterfaceFeignClient {

    @GetMapping("/interface/internal/by-code/{code}")
    Result<ApiInterfaceDTO> getByInterfaceCode(@PathVariable("code") String interfaceCode);

    @GetMapping("/interface/internal/{id}")
    Result<ApiInterfaceDTO> getById(@PathVariable("id") Long id);
}
