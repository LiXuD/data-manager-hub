package com.dataplatform.masterdata.interface_.controller;

import com.dataplatform.api.Result;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.InterfaceParam;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.service.InterfaceParamService;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.common.security.InternalScope;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主数据域接口定义的 Api Interface Internal Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/internal/v1/masterdata/interfaces")
@InternalScope("masterdata:read")
public class ApiInterfaceInternalController implements ApiInterfaceFeignClient {

    @Autowired
    private ApiInterfaceService apiInterfaceService;

    @Autowired
    private InterfaceParamService interfaceParamService;

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

    @GetMapping("/{id}/params")
    @Override
    public Result<List<InterfaceParamDTO>> listParams(@PathVariable("id") Long id) {
        if (apiInterfaceService.getById(id) == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(interfaceParamService.listByInterfaceId(id).stream()
                .map(this::toParamDTO)
                .toList());
    }

    private ApiInterfaceDTO toDTO(ApiInterface entity) {
        ApiInterfaceDTO dto = new ApiInterfaceDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private InterfaceParamDTO toParamDTO(InterfaceParam entity) {
        InterfaceParamDTO dto = new InterfaceParamDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
