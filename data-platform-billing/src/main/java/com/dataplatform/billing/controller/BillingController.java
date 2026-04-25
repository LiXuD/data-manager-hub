package com.dataplatform.billing.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.dataplatform.common.constant.StatusConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/billing")
public class BillingController {

    @Autowired
    private BillingService billingService;

    private static final List<String> VALID_STATUSES = List.of(StatusConstants.ACTIVE, StatusConstants.INACTIVE, StatusConstants.PENDING);

    @GetMapping("/list")
    public Result<List<BillingDaily>> list() {
        return Result.success(billingService.list());
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
    public Result<Map<String, Object>> stats() {
        return Result.success(billingService.getStats());
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
        rule.setId(null);
        rule.setStatus(StatusConstants.ACTIVE);
        billingService.saveRule(rule);
        return ResponseEntity.ok(Result.success(rule));
    }

    @PutMapping("/rule/{id}")
    public ResponseEntity<Result<BillingRule>> updateRule(@PathVariable Long id, @RequestBody BillingRule rule) {
        BillingRule existing = billingService.getRuleById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "计费规则不存在"));
        }
        rule.setId(id);
        billingService.updateRule(rule);
        return ResponseEntity.ok(Result.success(billingService.getRuleById(id)));
    }

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
}
