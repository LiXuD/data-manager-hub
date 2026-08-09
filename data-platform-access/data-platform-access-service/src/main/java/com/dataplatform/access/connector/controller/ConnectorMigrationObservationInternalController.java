package com.dataplatform.access.connector.controller;

import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationReqDTO;
import com.dataplatform.access.connector.api.feign.ConnectorMigrationObservationInternalFeignClient;
import com.dataplatform.access.connector.service.ConnectorMigrationObservationService;
import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/access/connector-migrations")
public class ConnectorMigrationObservationInternalController
        implements ConnectorMigrationObservationInternalFeignClient {
    private final ConnectorMigrationObservationService service;

    public ConnectorMigrationObservationInternalController(ConnectorMigrationObservationService service) {
        this.service = service;
    }

    @Override
    @InternalScope("access:connector-runtime:read")
    public Result<ConnectorMigrationObservationDTO> observation(ConnectorMigrationObservationReqDTO request) {
        return Result.success(service.observe(request));
    }
}
