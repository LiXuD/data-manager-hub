package com.dataplatform.billing.controller;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.result.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.entity.BillingReconciliation;
import com.dataplatform.billing.service.BillingReconciliationException;
import com.dataplatform.billing.service.BillingService;
import com.dataplatform.billing.service.ReconciliationService;
import com.dataplatform.common.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 计费域计费计算的 Billing Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/billing")
public class BillingController {

    @Autowired
    private BillingService billingService;

    @Autowired
    private ReconciliationService reconciliationService;

    @GetMapping("/list")
    public PageResult<BillingDaily> list(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        if (!canViewBilling() || !canAccessTenant(tenantId)) {
            return forbiddenPage();
        }
        Long scopedTenantId = scopedTenantId(tenantId);
        Page<BillingDaily> result = billingService.pageQuery(scopedTenantId, vendorId, startDate, endDate, page, pageSize);
        PageResult<BillingDaily> response = new PageResult<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<BillingDaily>> getById(@PathVariable Long id) {
        if (!canViewBilling()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(403, "没有计费管理查看权限"));
        }
        BillingDaily billing = billingService.getById(id);
        if (billing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "账单不存在"));
        }
        if (!canAccessTenant(billing.getTenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(403, "不能查看其他租户的账单"));
        }
        return ResponseEntity.ok(Result.success(billing));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (!canViewBilling() || !canAccessTenant(tenantId)) {
            return Result.error(403, "没有该租户的计费查看权限");
        }
        return Result.success(billingService.getBillingStats(scopedTenantId(tenantId), startDate, endDate));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (!canViewBilling() || !canAccessTenant(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] data = billingService.export(scopedTenantId(tenantId), vendorId, startDate, endDate);
        String filename = "billing_export_" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(data.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }

    @OperationLog(module = "自动对账", operation = "导入厂商账单")
    @PostMapping(value = "/reconciliation/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> importVendorBill(@RequestPart("file") MultipartFile file) {
        if (!allowed("billing:reconcile")) {
            return Result.error(403, "没有计费对账权限");
        }
        if (file == null || file.isEmpty()) {
            return Result.error(400, "账单文件不能为空");
        }
        final int imported;
        try {
            imported = reconciliationService.importVendorBills(new String(file.getBytes()));
        } catch (IOException exception) {
            return Result.error(400, "账单文件读取失败");
        }
        return Result.success(Map.of("imported", imported));
    }

    @OperationLog(module = "自动对账", operation = "运行对账")
    @PostMapping("/reconciliation/run")
    public Result<Void> runReconciliation(
            @RequestParam(required = false) Long vendorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billingDate) {
        if (!allowed("billing:reconcile")) {
            return Result.error(403, "没有计费对账权限");
        }
        reconciliationService.reconcile(vendorId, billingDate);
        return Result.success(null);
    }

    @GetMapping("/reconciliation/list")
    public Result<List<BillingReconciliation>> listReconciliation(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        if (!allowed("billing:reconcile")) {
            return Result.error(403, "没有计费对账权限");
        }
        return Result.success(reconciliationService.list(vendorId, startDate, endDate, page, pageSize).getRecords());
    }

    @GetMapping("/reconciliation/diffs")
    public Result<List<BillingReconciliation>> listReconciliationDiffs(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (!allowed("billing:reconcile")) {
            return Result.error(403, "没有计费对账权限");
        }
        return Result.success(reconciliationService.listDiffs(vendorId, startDate, endDate));
    }

    @ExceptionHandler(BillingReconciliationException.class)
    public ResponseEntity<Result<Void>> handleReconciliationException(
            BillingReconciliationException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Result.error(exception.getStatus(),
                        exception.getErrorCode() + ": " + exception.getMessage()));
    }

    private boolean canViewBilling() {
        return allowed("billing:view");
    }

    private boolean canAccessTenant(Long requestedTenantId) {
        if (allowed("billing:view-all")) {
            return true;
        }
        Long currentTenantId = UserContext.getCurrentTenantId();
        return currentTenantId != null
                && (requestedTenantId == null || currentTenantId.equals(requestedTenantId));
    }

    private Long scopedTenantId(Long requestedTenantId) {
        return allowed("billing:view-all")
                ? requestedTenantId
                : UserContext.getCurrentTenantId();
    }

    private boolean allowed(String permission) {
        return UserContext.hasPermission(permission)
                || UserContext.hasPermission("system:admin");
    }

    private PageResult<BillingDaily> forbiddenPage() {
        PageResult<BillingDaily> response = new PageResult<>();
        response.setCode(403);
        response.setMessage("没有该租户的计费查看权限");
        return response;
    }
}
