package com.dataplatform.identity.security.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.identity.api.dto.EncryptionReqDTO;
import com.dataplatform.identity.api.feign.EncryptionInternalFeignClient;
import com.dataplatform.identity.security.service.EncryptionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/identity/encryption")
@InternalScope("identity:encryption")
public class EncryptionInternalController implements EncryptionInternalFeignClient {

    private final EncryptionService encryptionService;

    public EncryptionInternalController(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    @PostMapping("/encrypt")
    public Result<String> encrypt(@RequestBody EncryptionReqDTO request) {
        validate(request);
        return Result.success(encryptionService.encrypt(request.getText(), request.getTableName()));
    }

    @Override
    @PostMapping("/decrypt")
    public Result<String> decrypt(@RequestBody EncryptionReqDTO request) {
        validate(request);
        return Result.success(encryptionService.decrypt(request.getText(), request.getTableName()));
    }

    private void validate(EncryptionReqDTO request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new IllegalArgumentException("加解密内容不能为空");
        }
        if (!"vendor_config_extended".equals(request.getTableName())) {
            throw new IllegalArgumentException("不允许的加解密数据范围");
        }
    }
}
