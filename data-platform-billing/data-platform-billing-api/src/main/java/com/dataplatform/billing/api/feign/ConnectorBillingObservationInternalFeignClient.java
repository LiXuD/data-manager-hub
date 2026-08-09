package com.dataplatform.billing.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationDTO;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationReqDTO;
import com.dataplatform.common.security.InternalFeignContract;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "data-platform-billing", contextId = "billingConnectorObservationInternalClient",
        path = "/internal/v1/billing/connector-migrations")
@InternalFeignContract
public interface ConnectorBillingObservationInternalFeignClient {

    @PostMapping("/observation")
    Result<ConnectorBillingObservationDTO> observation(@RequestBody ConnectorBillingObservationReqDTO request);
}
