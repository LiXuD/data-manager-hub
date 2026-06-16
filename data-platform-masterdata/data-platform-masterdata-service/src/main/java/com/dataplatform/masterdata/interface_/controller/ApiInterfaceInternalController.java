package com.dataplatform.masterdata.interface_.controller;

import com.dataplatform.api.Result;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/interface/internal")
public class ApiInterfaceInternalController implements ApiInterfaceFeignClient {

    @Autowired
    private ApiInterfaceService apiInterfaceService;

    @GetMapping("/by-code/{code}")
    @Override
    public Result<ApiInterfaceDTO> getByInterfaceCode(@PathVariable("code") String interfaceCode) {
        ApiInterface entity = apiInterfaceService.getByInterfaceCode(interfaceCode);
        if (entity == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(entity));
    }

    @GetMapping("/{id}")
    @Override
    public Result<ApiInterfaceDTO> getById(@PathVariable("id") Long id) {
        ApiInterface entity = apiInterfaceService.getById(id);
        if (entity == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(entity));
    }

    private ApiInterfaceDTO toDTO(ApiInterface entity) {
        ApiInterfaceDTO dto = new ApiInterfaceDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
