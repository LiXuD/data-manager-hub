package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.result.Result;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访问域调用方的 Caller Product Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/caller/{callerId}/products")
public class CallerProductController {

    private final CallerService callerService;
    private final CallerProductService callerProductService;

    public CallerProductController(CallerService callerService, CallerProductService callerProductService) {
        this.callerService = callerService;
        this.callerProductService = callerProductService;
    }

    @GetMapping
    public Result<List<CallerProduct>> list(@PathVariable Long callerId) {
        return Result.success(callerProductService.listByCaller(callerId));
    }

    @PostMapping
    public ResponseEntity<Result<CallerProduct>> create(@PathVariable Long callerId,
                                                        @RequestBody CallerProduct product) {
        if (callerService.getById(callerId) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "调用方不存在"));
        }
        if (product.getProductCode() == null || product.getProductCode().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "productCode不能为空"));
        }
        if (product.getProductName() == null || product.getProductName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "productName不能为空"));
        }
        return ResponseEntity.ok(Result.success(callerProductService.saveProduct(callerId, product)));
    }
}
