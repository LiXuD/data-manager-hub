package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CallerProductException;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    private static final Set<String> CACHE_SCOPES = Set.of("GLOBAL", "CALLER");
    private static final Set<String> STATUSES = Set.of(StatusConstants.ACTIVE, StatusConstants.INACTIVE);

    private final CallerService callerService;
    private final CallerProductService callerProductService;

    public CallerProductController(CallerService callerService, CallerProductService callerProductService) {
        this.callerService = callerService;
        this.callerProductService = callerProductService;
    }

    @GetMapping
    public ResponseEntity<Result<List<CallerProduct>>> list(@PathVariable Long callerId) {
        if (!tenantAllowed(callerId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "调用方不存在"));
        }
        return ResponseEntity.ok(Result.success(callerProductService.listByCaller(callerId)));
    }

    @PostMapping
    public ResponseEntity<Result<CallerProduct>> create(@PathVariable Long callerId,
                                                        @RequestBody CallerProduct product) {
        if (!tenantAllowed(callerId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "调用方不存在"));
        }
        String validationError = validate(product, true);
        if (validationError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, validationError));
        }
        try {
            return ResponseEntity.ok(Result.success(callerProductService.saveProduct(callerId, product)));
        } catch (CallerProductException exception) {
            return error(exception);
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "产品编码已存在或产品数据冲突"));
        }
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Result<CallerProduct>> update(@PathVariable Long callerId,
                                                        @PathVariable Long productId,
                                                        @RequestBody CallerProduct product) {
        if (!tenantAllowed(callerId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "调用方不存在"));
        }
        String validationError = validate(product, false);
        if (validationError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, validationError));
        }
        CallerProduct updated;
        try {
            updated = callerProductService.updateProduct(callerId, productId, product);
        } catch (CallerProductException exception) {
            return error(exception);
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "产品编码已存在或产品数据冲突"));
        }
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "产品不存在或不属于该调用方"));
        }
        return ResponseEntity.ok(Result.success(updated));
    }

    private boolean tenantAllowed(Long callerId) {
        CallerInfo caller = callerService.getById(callerId);
        return caller != null && (UserContext.hasPermission("system:admin")
                || (UserContext.getCurrentTenantId() != null
                && UserContext.getCurrentTenantId().equals(caller.getTenantId())));
    }

    private String validate(CallerProduct product, boolean requireCode) {
        if (product == null) {
            return "请求体不能为空";
        }
        if (requireCode && (product.getProductCode() == null || product.getProductCode().trim().isEmpty())) {
            return "productCode不能为空";
        }
        if (product.getProductName() == null || product.getProductName().trim().isEmpty()) {
            return "productName不能为空";
        }
        if (product.getCacheScope() != null
                && !product.getCacheScope().trim().isEmpty()
                && !CACHE_SCOPES.contains(product.getCacheScope().trim().toUpperCase())) {
            return "cacheScope必须是GLOBAL或CALLER";
        }
        if (product.getStatus() != null
                && !product.getStatus().trim().isEmpty()
                && !STATUSES.contains(product.getStatus().trim().toLowerCase())) {
            return "status必须是active或inactive";
        }
        return null;
    }

    private <T> ResponseEntity<Result<T>> error(CallerProductException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Result.error(exception.getStatus(),
                        exception.getErrorCode() + ": " + exception.getMessage()));
    }
}
