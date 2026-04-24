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
            @RequestParam(required = false) String baseUrl,
            @RequestParam(required = false) String apiKey) {
        // 参数验证
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "baseUrl不能为空"));
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "apiKey不能为空"));
        }

        String code = sdkGeneratorService.generateJavaSDK(baseUrl, apiKey);
        return ResponseEntity.ok(Result.success(code));
    }

    @GetMapping("/python")
    public ResponseEntity<Result<String>> generatePython(
            @RequestParam(required = false) String baseUrl,
            @RequestParam(required = false) String apiKey) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "baseUrl不能为空"));
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "apiKey不能为空"));
        }

        String code = sdkGeneratorService.generatePythonSDK(baseUrl, apiKey);
        return ResponseEntity.ok(Result.success(code));
    }

    @GetMapping("/go")
    public ResponseEntity<Result<String>> generateGo(
            @RequestParam(required = false) String baseUrl,
            @RequestParam(required = false) String apiKey) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "baseUrl不能为空"));
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "apiKey不能为空"));
        }

        String code = sdkGeneratorService.generateGoSDK(baseUrl, apiKey);
        return ResponseEntity.ok(Result.success(code));
    }

    @GetMapping("/all")
    public ResponseEntity<Result<Map<String, String>>> generateAll(
            @RequestParam(required = false) String baseUrl,
            @RequestParam(required = false) String apiKey) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "baseUrl不能为空"));
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "apiKey不能为空"));
        }

        Map<String, String> sdks = sdkGeneratorService.generateAllSDKs(baseUrl, apiKey);
        return ResponseEntity.ok(Result.success(sdks));
    }
}