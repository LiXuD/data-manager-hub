package com.dataplatform.masterdata.graylog.api.feign;

import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleCreateReqDTO;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleDTO;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleUpdateReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 主数据域灰度规则的 Graylog Feign Client。
 * <p>OpenFeign 远程调用契约，供其他服务依赖 api 模块完成跨域调用。</p>
 */
@FeignClient(name = "data-platform-masterdata", contextId = "masterdataGraylogFeignClient")
public interface GraylogFeignClient {

    @GetMapping("/graylog/list")
    PageResult<GrayRuleDTO> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize);

    @GetMapping("/graylog/{id}")
    Result<GrayRuleDTO> get(@PathVariable("id") Long id);

    @PostMapping("/graylog")
    Result<GrayRuleDTO> create(@RequestBody GrayRuleCreateReqDTO dto);

    @PutMapping("/graylog/{id}")
    Result<GrayRuleDTO> update(@PathVariable("id") Long id, @RequestBody GrayRuleUpdateReqDTO dto);

    @DeleteMapping("/graylog/{id}")
    Result<Void> delete(@PathVariable("id") Long id);

    @GetMapping("/graylog/active/{serviceName}")
    Result<GrayRuleDTO> getActiveRule(@PathVariable("serviceName") String serviceName);

    @PatchMapping("/graylog/{id}/status")
    Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body);
}
