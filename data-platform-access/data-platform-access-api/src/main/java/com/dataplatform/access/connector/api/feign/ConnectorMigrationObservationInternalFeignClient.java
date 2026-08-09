package com.dataplatform.access.connector.api.feign;

import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationReqDTO;
import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalFeignContract;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "data-platform-access", contextId = "accessConnectorMigrationObservationInternalClient",
        path = "/internal/v1/access/connector-migrations")
@InternalFeignContract
public interface ConnectorMigrationObservationInternalFeignClient {

    @PostMapping("/observation")
    Result<ConnectorMigrationObservationDTO> observation(
            @RequestBody ConnectorMigrationObservationReqDTO request);
}
