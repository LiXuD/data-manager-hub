package com.dataplatform.sdk.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.sdk.service.SDKGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sdk")
public class SDKController {

    @Autowired
    private SDKGeneratorService sdkGeneratorService;

    @GetMapping("/java")
    public Result<String> generateJava(@RequestParam String baseUrl,
                                        @RequestParam String apiKey) {
        String code = sdkGeneratorService.generateJavaSDK(baseUrl, apiKey);
        return Result.success(code);
    }

    @GetMapping("/python")
    public Result<String> generatePython(@RequestParam String baseUrl,
                                          @RequestParam String apiKey) {
        String code = sdkGeneratorService.generatePythonSDK(baseUrl, apiKey);
        return Result.success(code);
    }

    @GetMapping("/go")
    public Result<String> generateGo(@RequestParam String baseUrl,
                                      @RequestParam String apiKey) {
        String code = sdkGeneratorService.generateGoSDK(baseUrl, apiKey);
        return Result.success(code);
    }

    @GetMapping("/all")
    public Result<Map<String, String>> generateAll(@RequestParam String baseUrl,
                                                    @RequestParam String apiKey) {
        Map<String, String> sdks = sdkGeneratorService.generateAllSDKs(baseUrl, apiKey);
        return Result.success(sdks);
    }
}