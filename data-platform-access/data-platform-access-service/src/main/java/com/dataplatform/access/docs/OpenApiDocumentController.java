package com.dataplatform.access.docs;

import com.dataplatform.api.Result;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.common.util.UserContext;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端接口文档预览和下载。 */
@RestController
@RequestMapping("/openapi-docs/interfaces")
public class OpenApiDocumentController {

    private final ApiInterfaceFeignClient apiInterfaceFeignClient;
    private final OpenApiDocumentService documentService;
    private final OpenApiBaseUrlResolver baseUrlResolver;

    public OpenApiDocumentController(ApiInterfaceFeignClient apiInterfaceFeignClient,
                                     OpenApiDocumentService documentService,
                                     OpenApiBaseUrlResolver baseUrlResolver) {
        this.apiInterfaceFeignClient = apiInterfaceFeignClient;
        this.documentService = documentService;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        if (!UserContext.hasPermission("interface:view")) {
            return Result.error(403, "没有接口文档查看权限");
        }
        InterfaceContractDTO contract = contract(id);
        if (contract == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(documentService.describe(contract, baseUrlResolver.resolve()));
    }

    @GetMapping("/{id}/openapi")
    public ResponseEntity<String> download(@PathVariable("id") Long id,
                                           @RequestParam(defaultValue = "json") String format) {
        if (!UserContext.hasPermission("interface:view")) {
            return ResponseEntity.status(403).body("没有接口文档查看权限");
        }
        InterfaceContractDTO contract = contract(id);
        if (contract == null) {
            return ResponseEntity.notFound().build();
        }
        String content = documentService.serialize(
                documentService.buildDocument(contract, baseUrlResolver.resolve()), format);
        MediaType mediaType = "yaml".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("application/yaml") : MediaType.APPLICATION_JSON;
        return ResponseEntity.ok().contentType(mediaType).body(content);
    }

    private InterfaceContractDTO contract(Long id) {
        Result<InterfaceContractDTO> result = apiInterfaceFeignClient.getContract(id);
        return result != null ? result.getData() : null;
    }
}
