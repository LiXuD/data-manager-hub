package com.dataplatform.billing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.entity.BillingTemplate;
import com.dataplatform.billing.model.BillingPlanModel;
import com.dataplatform.billing.model.BillingSimulationCommand;
import com.dataplatform.billing.model.BillingSimulationResult;
import com.dataplatform.billing.model.BillingReversalCommand;
import com.dataplatform.billing.service.BillingContractReviewService;
import com.dataplatform.billing.service.BillingEventQueryService;
import com.dataplatform.billing.service.BillingPlanService;
import com.dataplatform.billing.service.BillingRecurringChargeService;
import com.dataplatform.billing.service.BillingReversalService;
import com.dataplatform.billing.service.BillingSimulationService;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 计费模板、方案生命周期、模拟和事件账本管理接口。 */
@RestController
@RequestMapping("/billing")
public class BillingPlanController {

    private final BillingPlanService planService;
    private final BillingSimulationService simulationService;
    private final BillingRecurringChargeService recurringChargeService;
    private final BillingEventQueryService eventQueryService;
    private final BillingReversalService reversalService;
    private final BillingContractReviewService contractReviewService;

    public BillingPlanController(BillingPlanService planService,
                                 BillingSimulationService simulationService,
                                 BillingRecurringChargeService recurringChargeService,
                                 BillingEventQueryService eventQueryService,
                                 BillingReversalService reversalService,
                                 BillingContractReviewService contractReviewService) {
        this.planService = planService;
        this.simulationService = simulationService;
        this.recurringChargeService = recurringChargeService;
        this.eventQueryService = eventQueryService;
        this.reversalService = reversalService;
        this.contractReviewService = contractReviewService;
    }

    @GetMapping("/template/list")
    public Result<List<BillingTemplate>> templates() {
        if (!UserContext.hasPermission("billing:view")) {
            return Result.error(403, "没有计费管理查看权限");
        }
        return Result.success(planService.listTemplates());
    }

    @GetMapping("/plan/list")
    public Result<List<BillingPlanModel>> plans() {
        if (!UserContext.hasPermission("billing:view")) {
            return Result.error(403, "没有计费管理查看权限");
        }
        return Result.success(planService.listPlans());
    }

    @GetMapping("/plan/{id}")
    public Result<BillingPlanModel> plan(@PathVariable Long id) {
        if (!UserContext.hasPermission("billing:view")) {
            return Result.error(403, "没有计费管理查看权限");
        }
        return Result.success(planService.get(id));
    }

    @OperationLog(module = "计费方案管理", operation = "创建计费方案草稿")
    @PostMapping("/plan")
    public Result<BillingPlanModel> create(@RequestBody BillingPlanModel command) {
        if (!UserContext.hasPermission("billing:manage")) {
            return Result.error(403, "没有计费方案管理权限");
        }
        return Result.success(planService.createDraft(command));
    }

    @OperationLog(module = "计费方案管理", operation = "更新计费方案草稿")
    @PutMapping("/plan/{id}")
    public Result<BillingPlanModel> update(@PathVariable Long id, @RequestBody BillingPlanModel command) {
        if (!UserContext.hasPermission("billing:manage")) {
            return Result.error(403, "没有计费方案管理权限");
        }
        return Result.success(planService.updateDraft(id, command));
    }

    @OperationLog(module = "计费方案管理", operation = "创建计费方案新版本")
    @PostMapping("/plan/{id}/next-version")
    public Result<BillingPlanModel> nextVersion(@PathVariable Long id) {
        if (!UserContext.hasPermission("billing:manage")) {
            return Result.error(403, "没有计费方案管理权限");
        }
        return Result.success(planService.createNextVersion(id));
    }

    @PostMapping("/plan/{id}/validate")
    public Result<Map<String, Object>> validate(@PathVariable Long id) {
        if (!UserContext.hasPermission("billing:view")) {
            return Result.error(403, "没有计费管理查看权限");
        }
        List<String> errors = planService.validate(id);
        return Result.success(Map.of("valid", errors.isEmpty(), "errors", errors));
    }

    @PostMapping("/plan/{id}/simulate")
    public Result<BillingSimulationResult> simulate(@PathVariable Long id,
                                                    @RequestBody BillingSimulationCommand command) {
        if (!UserContext.hasPermission("billing:view")) {
            return Result.error(403, "没有计费管理查看权限");
        }
        return Result.success(simulationService.simulate(id, command));
    }

    @OperationLog(module = "计费方案管理", operation = "发布计费方案")
    @PostMapping("/plan/{id}/publish")
    public Result<BillingPlanModel> publish(@PathVariable Long id) {
        if (!UserContext.hasPermission("billing:manage")) {
            return Result.error(403, "没有计费方案管理权限");
        }
        return Result.success(planService.publish(id));
    }

    @OperationLog(module = "计费方案管理", operation = "删除计费方案草稿")
    @DeleteMapping("/plan/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!UserContext.hasPermission("billing:manage")) {
            return Result.error(403, "没有计费方案管理权限");
        }
        planService.deleteDraft(id);
        return Result.success(null);
    }

    @OperationLog(module = "计费方案管理", operation = "补提周期固定费用")
    @PostMapping("/plan/accrue")
    public Result<Map<String, Integer>> accrue(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!UserContext.hasPermission("billing:manage")) {
            return Result.error(403, "没有计费方案管理权限");
        }
        LocalDateTime at = date != null ? date.atStartOfDay() : LocalDateTime.now();
        return Result.success(Map.of("created", recurringChargeService.accrue(at)));
    }

    @OperationLog(module = "计费方案管理", operation = "检查响应契约变更")
    @PostMapping("/plan/review-contracts")
    public Result<Map<String, Object>> reviewContracts() {
        if (!UserContext.hasPermission("billing:manage")) {
            return Result.error(403, "没有计费方案管理权限");
        }
        return Result.success(contractReviewService.review());
    }

    @GetMapping("/event/list")
    public PageResult<BillingEvent> events(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long interfaceId,
            @RequestParam(required = false) String accountingPurpose,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        if (!UserContext.hasPermission("billing:view")) {
            PageResult<BillingEvent> forbidden = new PageResult<>();
            forbidden.setCode(403);
            forbidden.setMessage("没有计费管理查看权限");
            return forbidden;
        }
        Page<BillingEvent> result = eventQueryService.page(
                tenantId, vendorId, interfaceId, accountingPurpose, status,
                startTime, endTime, page, pageSize);
        PageResult<BillingEvent> response = new PageResult<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    @GetMapping("/event/stats")
    public Result<Map<String, Object>> eventStats(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long interfaceId,
            @RequestParam(required = false) String accountingPurpose,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        if (!UserContext.hasPermission("billing:view")) {
            return Result.error(403, "没有计费管理查看权限");
        }
        return Result.success(eventQueryService.stats(
                tenantId, vendorId, interfaceId, accountingPurpose, startTime, endTime));
    }

    @OperationLog(module = "计费事件管理", operation = "冲正计费事件")
    @PostMapping("/event/{id}/reverse")
    public Result<BillingEvent> reverse(@PathVariable Long id,
                                        @RequestBody BillingReversalCommand command) {
        if (!UserContext.hasPermission("billing:reverse")) {
            return Result.error(403, "没有计费事件冲正权限");
        }
        return Result.success(reversalService.reverse(id, command));
    }
}
