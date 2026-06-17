package com.dataplatform.masterdata.interface_.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 主数据域接口定义的 Api Interface Feign Client。
 * <p>OpenFeign 远程调用契约，供其他服务依赖 api 模块完成跨域调用。</p>
 */
@FeignClient(name = "data-platform-masterdata", contextId = "masterdataApiInterfaceClient")
public interface ApiInterfaceFeignClient {

    @GetMapping("/interface/internal/by-code/{code}")
    Result<ApiInterfaceDTO> getByInterfaceCode(@PathVariable("code") String interfaceCode);

    @GetMapping("/interface/internal/{id}")
    Result<ApiInterfaceDTO> getById(@PathVariable("id") Long id);
}
