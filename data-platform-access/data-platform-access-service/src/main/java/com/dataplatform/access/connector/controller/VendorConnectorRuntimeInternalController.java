package com.dataplatform.access.connector.controller;

import com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.access.connector.api.feign.VendorConnectorRuntimeInternalFeignClient;
import com.dataplatform.access.connector.service.VendorConnectorControlledTestService;
import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.security.InternalScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/access/vendor-connectors")
public class VendorConnectorRuntimeInternalController
        implements VendorConnectorRuntimeInternalFeignClient {

    private final VendorConnectorControlledTestService service;

    public VendorConnectorRuntimeInternalController(VendorConnectorControlledTestService service) {
        this.service = service;
    }

    @Override
    @InternalScope("access:connector-runtime:test")
    @OperationLog(module = "连接器运行时", operation = "受控测试连接器草稿",
            saveParams = false, saveResult = false)
    public Result<VendorConnectorTestRespDTO> test(VendorConnectorTestReqDTO request) {
        return Result.success(service.test(request));
    }
}
