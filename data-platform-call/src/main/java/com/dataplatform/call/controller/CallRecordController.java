package com.dataplatform.call.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.call.entity.CallRecord;
import com.dataplatform.call.service.CallRecordService;
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/call/record")
public class CallRecordController {
    
    @Autowired
    private CallRecordService callRecordService;
    
    @GetMapping("/list")
    public Result<List<CallRecord>> list() {
        return Result.success(callRecordService.list());
    }
    
    @GetMapping("/{id}")
    public Result<CallRecord> getById(@PathVariable Long id) {
        return Result.success(callRecordService.getById(id));
    }
}