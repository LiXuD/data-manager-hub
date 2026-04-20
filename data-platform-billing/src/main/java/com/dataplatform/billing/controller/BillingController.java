package com.dataplatform.billing.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.service.BillingService;
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/billing")
public class BillingController {
    
    @Autowired
    private BillingService billingService;
    
    @GetMapping("/list")
    public Result<List<BillingDaily>> list() {
        return Result.success(billingService.list());
    }
    
    @GetMapping("/{id}")
    public Result<BillingDaily> getById(@PathVariable Long id) {
        return Result.success(billingService.getById(id));
    }
}