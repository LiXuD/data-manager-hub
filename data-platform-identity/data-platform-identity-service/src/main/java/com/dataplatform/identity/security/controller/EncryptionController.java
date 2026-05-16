package com.dataplatform.identity.security.controller;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.identity.security.service.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/security/encryption")
public class EncryptionController {

    @Autowired
    private EncryptionService encryptionService;

    @OperationLog(module = "安全加密", operation = "数据加密")
    @PostMapping("/encrypt")
    public Result<String> encrypt(
            @RequestParam(required = false) String plainText,
            @RequestParam(required = false) String tableName) {
        if (plainText == null || plainText.trim().isEmpty()) {
            return Result.error(400, "plainText不能为空");
        }
        if (tableName == null || tableName.trim().isEmpty()) {
            return Result.error(400, "tableName不能为空");
        }

        String encrypted = encryptionService.encrypt(plainText, tableName);
        return Result.success(encrypted);
    }

    @OperationLog(module = "安全加密", operation = "数据解密")
    @PostMapping("/decrypt")
    public Result<String> decrypt(
            @RequestParam(required = false) String encryptedText,
            @RequestParam(required = false) String tableName) {
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            return Result.error(400, "encryptedText不能为空");
        }
        if (tableName == null || tableName.trim().isEmpty()) {
            return Result.error(400, "tableName不能为空");
        }

        String decrypted = encryptionService.decrypt(encryptedText, tableName);
        return Result.success(decrypted);
    }

    @OperationLog(module = "安全加密", operation = "密钥轮换")
    @PostMapping("/rotate/{tableName}")
    public Result<Boolean> rotateKey(@PathVariable String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            return Result.error(400, "表名不能为空");
        }
        if (!isValidTableName(tableName)) {
            return Result.error(404, "表不存在");
        }
        encryptionService.rotateKey(tableName);
        return Result.success(true);
    }

    private boolean isValidTableName(String tableName) {
        return tableName != null && !tableName.isEmpty() &&
               (tableName.equals("user_info") || tableName.equals("tenant_info") ||
                tableName.equals("vendor_info") || tableName.equals("caller_info"));
    }
}