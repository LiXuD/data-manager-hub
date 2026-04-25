package com.dataplatform.sdk.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.sdk.service.SDKGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/sdk")
public class SDKController {

    @Autowired
    private SDKGeneratorService sdkGeneratorService;

    @GetMapping("/java")
    public ResponseEntity<Result<String>> generateJava(
            @RequestParam String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "baseUrl不能为空"));
        }

        String code = sdkGeneratorService.generateJavaSDK(baseUrl);
        return ResponseEntity.ok(Result.success(code));
    }

    @GetMapping("/python")
    public ResponseEntity<Result<String>> generatePython(
            @RequestParam String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "baseUrl不能为空"));
        }

        String code = sdkGeneratorService.generatePythonSDK(baseUrl);
        return ResponseEntity.ok(Result.success(code));
    }

    @GetMapping("/go")
    public ResponseEntity<Result<String>> generateGo(
            @RequestParam String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "baseUrl不能为空"));
        }

        String code = sdkGeneratorService.generateGoSDK(baseUrl);
        return ResponseEntity.ok(Result.success(code));
    }

    @GetMapping("/all")
    public ResponseEntity<Result<Map<String, String>>> generateAll(
            @RequestParam String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "baseUrl不能为空"));
        }

        Map<String, String> sdks = sdkGeneratorService.generateAllSDKs(baseUrl);
        return ResponseEntity.ok(Result.success(sdks));
    }
}