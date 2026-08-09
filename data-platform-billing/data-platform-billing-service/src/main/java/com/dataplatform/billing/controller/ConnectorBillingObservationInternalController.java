package com.dataplatform.billing.controller;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationDTO;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationReqDTO;
import com.dataplatform.billing.api.feign.ConnectorBillingObservationInternalFeignClient;
import com.dataplatform.billing.service.ConnectorBillingObservationService;
import com.dataplatform.common.security.InternalScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/billing/connector-migrations")
public class ConnectorBillingObservationInternalController
        implements ConnectorBillingObservationInternalFeignClient {
    private final ConnectorBillingObservationService service;

    public ConnectorBillingObservationInternalController(ConnectorBillingObservationService service) {
        this.service = service;
    }

    @Override
    @InternalScope("billing:connector-observation:read")
    public Result<ConnectorBillingObservationDTO> observation(ConnectorBillingObservationReqDTO request) {
        return Result.success(service.observe(request));
    }
}
