package com.dataplatform.caller.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.caller.entity.ApiKey;
import com.dataplatform.caller.service.ApiKeyService;
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/caller/apikey")
public class ApiKeyController {
    
    @Autowired
    private ApiKeyService apiKeyService;
    
    @GetMapping("/list")
    public Result<List<ApiKey>> list() {
        return Result.success(apiKeyService.list());
    }
    
    @GetMapping("/{id}")
    public Result<ApiKey> getById(@PathVariable Long id) {
        return Result.success(apiKeyService.getById(id));
    }
}