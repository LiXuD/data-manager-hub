package com.dataplatform.masterdata.connector.spec.inventory;

import com.dataplatform.api.Result;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.ConnectorLegacyInventoryDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vendor/config/connector-spec")
public class ConnectorLegacyInventoryController {
    private final ConnectorLegacyInventoryService service;

    public ConnectorLegacyInventoryController(ConnectorLegacyInventoryService service) {
        this.service = service;
    }

    @GetMapping("/inventory")
    @OperationLog(module = "厂商连接器产品配置", operation = "清点 Legacy 连接器迁移分类",
            saveParams = false, saveResult = false)
    public Result<ConnectorLegacyInventoryDTO> inventory(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize) {
        if (!UserContext.hasPermission("connector-plugin:view")
                && !UserContext.hasPermission("system:admin")) {
            return Result.error(403, "没有厂商连接器查看权限");
        }
        return Result.success(service.inventory(page, pageSize));
    }
}
