package com.dataplatform.masterdata.datatype.api.feign;

import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.datatype.api.dto.DataTypeCreateReqDTO;
import com.dataplatform.masterdata.datatype.api.dto.DataTypeDTO;
import com.dataplatform.masterdata.datatype.api.dto.DataTypeUpdateReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "data-platform-masterdata", contextId = "masterdataDataTypeFeignClient")
public interface DataTypeFeignClient {

    @GetMapping("/datatype/list")
    PageResult<DataTypeDTO> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize);

    @GetMapping("/datatype/{id}")
    Result<DataTypeDTO> get(@PathVariable("id") Long id);

    @GetMapping("/datatype/code/{code}")
    Result<DataTypeDTO> getByCode(@PathVariable("code") String code);

    @PostMapping("/datatype")
    Result<DataTypeDTO> create(@RequestBody DataTypeCreateReqDTO dto);

    @PutMapping("/datatype/{id}")
    Result<DataTypeDTO> update(@PathVariable("id") Long id, @RequestBody DataTypeUpdateReqDTO dto);

    @DeleteMapping("/datatype/{id}")
    Result<Void> delete(@PathVariable("id") Long id);

    @PatchMapping("/datatype/{id}/status")
    Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body);

    @GetMapping("/datatype/all")
    Result<List<DataTypeDTO>> listAll();
}
