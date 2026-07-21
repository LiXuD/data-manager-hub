package com.dataplatform.billing.controller;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.result.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.entity.BillingReconciliation;
import com.dataplatform.billing.service.BillingService;
import com.dataplatform.billing.service.ReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.dataplatform.common.constant.StatusConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private static final List<String> VALID_STATUSES = List.of(StatusConstants.ACTIVE, StatusConstants.INACTIVE, StatusConstants.PENDING);

    @GetMapping("/list")
    public PageResult<BillingDaily> list(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<BillingDaily> result = billingService.pageQuery(tenantId, vendorId, startDate, endDate, page, pageSize);
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
        BillingDaily billing = billingService.getById(id);
        if (billing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "账单不存在"));
        }
        return ResponseEntity.ok(Result.success(billing));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(billingService.getBillingStats(tenantId, startDate, endDate));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] data = billingService.export();
        String filename = "billing_export_" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(data.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }

    @GetMapping("/rule/list")
    public Result<List<BillingRule>> listRules() {
        return Result.success(billingService.listRules());
    }

    @OperationLog(module = "计费规则管理", operation = "新增计费规则")
    @PostMapping("/rule")
    public ResponseEntity<Result<BillingRule>> createRule(@RequestBody BillingRule rule) {
        // 验证必填参数
        if (rule.getRuleName() == null || rule.getRuleName().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "ruleName不能为空"));
        }
        if (rule.getUnitPrice() == null || rule.getUnitPrice().doubleValue() < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "unitPrice无效"));
        }
        if (rule.getVendorId() == null || rule.getInterfaceId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "vendorId和interfaceId不能为空"));
        }
        rule.setId(null);
        rule.setStatus(StatusConstants.ACTIVE);
        try {
            billingService.saveRule(rule);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, exception.getMessage()));
        }
        return ResponseEntity.ok(Result.success(rule));
    }

    @OperationLog(module = "计费规则管理", operation = "更新计费规则")
    @PutMapping("/rule/{id}")
    public ResponseEntity<Result<BillingRule>> updateRule(@PathVariable Long id, @RequestBody BillingRule rule) {
        BillingRule existing = billingService.getRuleById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "计费规则不存在"));
        }
        rule.setId(id);
        try {
            billingService.updateRule(rule);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, exception.getMessage()));
        }
        return ResponseEntity.ok(Result.success(billingService.getRuleById(id)));
    }

    @OperationLog(module = "计费规则管理", operation = "删除计费规则")
    @DeleteMapping("/rule/{id}")
    public ResponseEntity<Result<Void>> deleteRule(@PathVariable Long id) {
        BillingRule existing = billingService.getRuleById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "计费规则不存在"));
        }
        billingService.deleteRule(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "自动对账", operation = "导入厂商账单")
    @PostMapping(value = "/reconciliation/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> importVendorBill(@RequestPart("file") MultipartFile file) throws Exception {
        int imported = reconciliationService.importVendorBills(new String(file.getBytes()));
        return Result.success(Map.of("imported", imported));
    }

    @OperationLog(module = "自动对账", operation = "运行对账")
    @PostMapping("/reconciliation/run")
    public Result<Void> runReconciliation(
            @RequestParam(required = false) Long vendorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billingDate) {
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
        return Result.success(reconciliationService.list(vendorId, startDate, endDate, page, pageSize).getRecords());
    }

    @GetMapping("/reconciliation/diffs")
    public Result<List<BillingReconciliation>> listReconciliationDiffs(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(reconciliationService.listDiffs(vendorId, startDate, endDate));
    }
}
