package com.dataplatform.masterdata.interface_.api.feign;

import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceCreateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceUpdateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@FeignClient(name = "data-platform-masterdata", contextId = "masterdataApiInterfaceManageClient")
public interface ApiInterfaceManageFeignClient {

    @GetMapping("/interface/list")
    PageResult<ApiInterfaceDTO> list(
            @RequestParam(value = "vendorId", required = false) Long vendorId,
            @RequestParam(value = "dataTypeId", required = false) Long dataTypeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize);

    @GetMapping("/interface/{id}")
    Result<ApiInterfaceDTO> getById(@PathVariable("id") Long id);

    @GetMapping("/interface/by-data-type/{dataTypeId}")
    Result<List<ApiInterfaceDTO>> listByDataType(@PathVariable("dataTypeId") Long dataTypeId);

    @PostMapping("/interface")
    Result<ApiInterfaceDTO> create(@RequestBody ApiInterfaceCreateReqDTO dto);

    @PutMapping("/interface/{id}")
    Result<ApiInterfaceDTO> update(@PathVariable("id") Long id, @RequestBody ApiInterfaceUpdateReqDTO dto);

    @DeleteMapping("/interface/{id}")
    Result<Void> delete(@PathVariable("id") Long id);

    @PatchMapping("/interface/{id}/status")
    Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body);

    @GetMapping("/interface/{id}/schema")
    Result<Map<String, Object>> getSchema(@PathVariable("id") Long id);

    @PutMapping("/interface/{id}/schema")
    Result<Void> updateSchema(@PathVariable("id") Long id, @RequestBody Map<String, String> body);

    @PostMapping("/interface/schema/validate")
    Result<Map<String, Object>> validateSchema(@RequestBody Map<String, String> body);

    @GetMapping("/interface/{id}/stats")
    Result<Map<String, Object>> getCallStats(
            @PathVariable("id") Long id,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime);

    @GetMapping("/interface/{id}/stats/daily")
    Result<List<Map<String, Object>>> getDailyCallStats(
            @PathVariable("id") Long id,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime);

    @GetMapping("/interface/{id}/params")
    Result<List<InterfaceParamDTO>> listParams(@PathVariable("id") Long id);

    @PostMapping("/interface/{id}/params")
    Result<InterfaceParamDTO> addParam(@PathVariable("id") Long id, @RequestBody InterfaceParamDTO dto);

    @PutMapping("/interface/{id}/params/batch")
    Result<Void> batchSaveParams(@PathVariable("id") Long id, @RequestBody List<InterfaceParamDTO> params);

    @PutMapping("/interface/params/{paramId}")
    Result<InterfaceParamDTO> updateParam(@PathVariable("paramId") Long paramId, @RequestBody InterfaceParamDTO dto);

    @DeleteMapping("/interface/params/{paramId}")
    Result<Void> deleteParam(@PathVariable("paramId") Long paramId);
}
