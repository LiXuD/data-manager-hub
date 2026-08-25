package com.dataplatform.masterdata.connector.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationDTO;
import com.dataplatform.masterdata.connector.service.VendorConnectorMigrationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Historical, read-only view of the completed connector migration program. */
@RestController
@RequestMapping("/vendor/connector-migration")
public class VendorConnectorMigrationController {
    private final VendorConnectorMigrationService service;

    public VendorConnectorMigrationController(VendorConnectorMigrationService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<VendorConnectorMigrationDTO>> list(
            @RequestParam(required = false) String state) {
        if (!UserContext.hasPermission("connector-plugin:view")) {
            return Result.error(403, "没有厂商连接器迁移记录查看权限");
        }
        return Result.success(service.list(state));
    }
}
